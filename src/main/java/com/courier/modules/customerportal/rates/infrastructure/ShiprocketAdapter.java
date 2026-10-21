package com.courier.modules.customerportal.rates.infrastructure;

import com.courier.modules.customerportal.rates.application.CustomerPortalProperties;
import com.courier.modules.customerportal.rates.domain.ChargeComponent;
import com.courier.modules.customerportal.rates.domain.ChargeLine;
import com.courier.modules.customerportal.rates.domain.CourierProviderAdapter;
import com.courier.modules.customerportal.rates.domain.ProviderRate;
import com.courier.modules.customerportal.rates.domain.ProviderResult;
import com.courier.modules.customerportal.rates.domain.RateRequest;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shiprocket (aggregator): one call returns every courier Shiprocket can use for the lane, with rates.
 *
 * <p>API as documented by Shiprocket ({@code POST /v1/external/auth/login}, {@code GET
 * /v1/external/courier/serviceability/}). Field names are read defensively; <b>verify against the
 * account's own responses before enabling</b> (see MEMORY/modules/customer-portal.md). Shiprocket's
 * quoted amount is shown as Shiprocket returns it; no tax line is invented when it does not split one out.
 */
@Slf4j
public class ShiprocketAdapter implements CourierProviderAdapter {

    public static final String CODE = "SHIPROCKET";
    private static final Duration TOKEN_LIFETIME = Duration.ofDays(9); // Shiprocket tokens last 10 days

    private final RestClient http;
    private final CustomerPortalProperties.Shiprocket config;
    private volatile String token;
    private volatile Instant tokenExpires = Instant.EPOCH;

    public ShiprocketAdapter(RestClient.Builder builder, CustomerPortalProperties.Shiprocket config) {
        this.config = config;
        this.http = builder.baseUrl(config.getBaseUrl()).build();
    }

    @Override public String code() { return CODE; }
    @Override public String displayName() { return "Shiprocket"; }
    @Override public boolean external() { return true; }
    @Override public boolean configured() { return config.configured(); }

    @Override
    public ProviderResult getRates(RateRequest r) {
        if (!configured()) {
            return ProviderResult.unavailable(CODE, displayName(), "Shiprocket is not connected yet.");
        }
        try {
            JsonNode body = http.get()
                    .uri(u -> u.path("/v1/external/courier/serviceability/")
                            .queryParam("pickup_postcode", r.pickupPincode())
                            .queryParam("delivery_postcode", r.deliveryPincode())
                            .queryParam("weight", r.actualWeightKg().toPlainString())
                            .queryParam("length", r.lengthCm().toPlainString())
                            .queryParam("breadth", r.breadthCm().toPlainString())
                            .queryParam("height", r.heightCm().toPlainString())
                            .queryParam("declared_value", r.declaredValue().toPlainString())
                            .queryParam("cod", r.cod() ? 1 : 0)
                            .build())
                    .header("Authorization", "Bearer " + token())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(JsonNode.class);
            return ProviderResult.available(CODE, displayName(), parse(body, r));
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                token = null; // forces a fresh login next time
            }
            log.warn("Shiprocket rates failed: HTTP {}", e.getStatusCode().value());
            return ProviderResult.unavailable(CODE, displayName(), "Shiprocket did not return rates for this shipment.");
        } catch (RestClientException | IllegalStateException e) {
            log.warn("Shiprocket rates failed: {}", e.getClass().getSimpleName());
            return ProviderResult.unavailable(CODE, displayName(), "Shiprocket could not be reached.");
        }
    }

    static List<ProviderRate> parse(JsonNode body, RateRequest r) {
        List<ProviderRate> rates = new ArrayList<>();
        JsonNode list = body == null ? null : body.path("data").path("available_courier_companies");
        if (list == null || !list.isArray()) {
            return rates;
        }
        for (JsonNode c : list) {
            BigDecimal total = decimal(c, "rate");
            if (total == null || total.signum() <= 0) {
                continue; // a service without a price is not offered
            }
            BigDecimal freight = decimal(c, "freight_charge");
            BigDecimal cod = r.cod() ? decimal(c, "cod_charges") : null;
            List<ChargeLine> lines = new ArrayList<>();
            BigDecimal accounted = BigDecimal.ZERO;
            if (freight != null && freight.signum() > 0 && freight.compareTo(total) <= 0) {
                lines.add(new ChargeLine("FREIGHT", "Freight", ChargeComponent.FREIGHT, freight));
                accounted = accounted.add(freight);
            }
            if (cod != null && cod.signum() > 0 && accounted.add(cod).compareTo(total) <= 0) {
                lines.add(new ChargeLine("COD", "COD charge", ChargeComponent.COD, cod));
                accounted = accounted.add(cod);
            }
            BigDecimal rest = total.subtract(accounted);
            if (lines.isEmpty()) {
                lines.add(new ChargeLine("FREIGHT", "Freight (as quoted)", ChargeComponent.FREIGHT, total));
            } else if (rest.signum() > 0) {
                lines.add(new ChargeLine("OTHER", "Other charges (as quoted)", ChargeComponent.OTHER, rest));
            }
            Integer days = integer(c, "estimated_delivery_days");
            String mode = c.has("is_surface") ? (c.path("is_surface").asBoolean() ? "SURFACE" : "AIR") : null;
            rates.add(new ProviderRate(c.path("courier_company_id").asText(null), c.path("courier_name").asText("Courier"),
                    mode, days, days, kg(c, "charge_weight"), lines, null, null));
        }
        return rates;
    }

    private synchronized String token() {
        if (token != null && Instant.now().isBefore(tokenExpires)) {
            return token;
        }
        JsonNode login = http.post().uri("/v1/external/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", config.getEmail(), "password", config.getPassword()))
                .retrieve()
                .body(JsonNode.class);
        String fresh = login == null ? null : login.path("token").asText(null);
        if (fresh == null || fresh.isBlank()) {
            throw new IllegalStateException("Shiprocket login returned no token");
        }
        token = fresh;
        tokenExpires = Instant.now().plus(TOKEN_LIFETIME);
        return fresh;
    }

    private static BigDecimal decimal(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull() || v.asText().isBlank()) return null;
        try {
            return new BigDecimal(v.asText()).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer integer(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull()) return null;
        try {
            return Integer.valueOf(v.asText().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal kg(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull() || v.asText().isBlank()) return null;
        try {
            return new BigDecimal(v.asText()).setScale(3, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
