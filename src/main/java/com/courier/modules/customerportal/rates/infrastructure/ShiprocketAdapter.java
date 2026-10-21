package com.courier.modules.customerportal.rates.infrastructure;

import com.courier.modules.customerportal.rates.application.CustomerPortalProperties;
import com.courier.modules.customerportal.rates.domain.BookingOutcome;
import com.courier.modules.customerportal.rates.domain.BookingRequest;
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
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
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

    /**
     * Two calls: create the order ({@code order_id} = our booking reference), then assign the chosen courier
     * and get the AWB. A failure after the order exists is UNKNOWN with the Shiprocket shipment id, so
     * operations can finish or cancel it in the Shiprocket panel — it is never created twice by us.
     */
    @Override
    public BookingOutcome book(BookingRequest b) {
        if (!config.bookable()) {
            return BookingOutcome.rejected("NOT_CONFIGURED", "Shiprocket bookings are not set up yet.");
        }
        String shipmentId;
        try {
            JsonNode order = http.post().uri("/v1/external/orders/create/adhoc")
                    .header("Authorization", "Bearer " + token())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(orderBody(b, config.getPickupLocation()))
                    .retrieve()
                    .body(JsonNode.class);
            shipmentId = order == null ? null : order.path("shipment_id").asText(null);
            if (shipmentId == null || shipmentId.isBlank()) {
                return BookingOutcome.unknown(null, "Shiprocket did not confirm the order. Our team will check it.");
            }
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status >= 400 && status < 500) {
                return BookingOutcome.rejected("PROVIDER_REJECTED", "Shiprocket refused the order. Please check the details.");
            }
            return BookingOutcome.unknown(null, "Shiprocket answered with an error. Our team will check whether the order was created.");
        } catch (ResourceAccessException e) {
            return BookingOutcome.unknown(null, "Shiprocket did not answer in time. Our team will check whether the order was created.");
        } catch (IllegalStateException e) {
            // Login failed: the order request was never sent.
            return BookingOutcome.rejected("PROVIDER_UNAVAILABLE", "Shiprocket could not be reached. Nothing was booked.");
        } catch (RestClientException e) {
            return BookingOutcome.unknown(null, "Shiprocket's answer could not be read. Our team will check whether the order was created.");
        }
        try {
            JsonNode awb = http.post().uri("/v1/external/courier/assign/awb")
                    .header("Authorization", "Bearer " + token())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("shipment_id", shipmentId, "courier_id", b.serviceCode()))
                    .retrieve()
                    .body(JsonNode.class);
            String code = awb == null ? null : awb.path("response").path("data").path("awb_code").asText(null);
            if (code == null || code.isBlank()) {
                return BookingOutcome.unknown(shipmentId, "The order was created but no tracking number was assigned yet. Our team will complete it.");
            }
            return BookingOutcome.booked(shipmentId, code, null, "Booked with Shiprocket.");
        } catch (RestClientException | IllegalStateException e) {
            return BookingOutcome.unknown(shipmentId, "The order was created but the courier could not be assigned yet. Our team will complete it.");
        }
    }

    static Map<String, Object> orderBody(BookingRequest b, String pickupLocation) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("order_id", b.reference());
        m.put("order_date", LocalDateTime.now(ZoneId.of("Asia/Kolkata")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        m.put("pickup_location", pickupLocation);
        m.put("billing_customer_name", b.delivery().name());
        m.put("billing_last_name", "");
        m.put("billing_address", b.delivery().address());
        m.put("billing_city", b.delivery().city());
        m.put("billing_pincode", b.delivery().pincode());
        m.put("billing_state", b.delivery().state());
        m.put("billing_country", "India");
        m.put("billing_phone", b.delivery().phone());
        m.put("shipping_is_billing", true);
        m.put("order_items", List.of(Map.of("name", b.contents() == null ? "Goods" : b.contents(), "sku", b.reference(),
                "units", b.rate().packageCount(), "selling_price", b.rate().declaredValue()
                        .divide(BigDecimal.valueOf(b.rate().packageCount()), 2, RoundingMode.HALF_UP))));
        m.put("payment_method", b.rate().cod() ? "COD" : "Prepaid");
        m.put("sub_total", b.rate().declaredValue());
        m.put("length", b.rate().lengthCm());
        m.put("breadth", b.rate().breadthCm());
        m.put("height", b.rate().heightCm());
        m.put("weight", b.rate().actualWeightKg());
        m.put("comment", "Pickup contact: " + b.pickup().name() + ", " + b.pickup().phone() + ", " + b.pickup().address()
                + ", " + b.pickup().pincode());
        return m;
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
