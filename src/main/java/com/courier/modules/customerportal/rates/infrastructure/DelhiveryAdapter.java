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
import java.util.ArrayList;
import java.util.List;

/**
 * Delhivery B2C: pincode serviceability ({@code GET /c/api/pin-codes/json/}) and shipping charges
 * ({@code GET /api/kinko/v1/invoice/charges/.json}) for Surface and Express, as documented by
 * Delhivery. Charges are asked for the chargeable weight max(actual, L x B x H / divisor x packages).
 * <b>Verify against the account's staging responses before enabling.</b>
 */
@Slf4j
public class DelhiveryAdapter implements CourierProviderAdapter {

    public static final String CODE = "DELHIVERY";
    private static final String[][] MODES = {{"S", "Surface", "SURFACE"}, {"E", "Express", "AIR"}};

    private final RestClient http;
    private final CustomerPortalProperties.Delhivery config;
    private final int volumetricDivisor;

    public DelhiveryAdapter(RestClient.Builder builder, CustomerPortalProperties.Delhivery config, int volumetricDivisor) {
        this.config = config;
        this.volumetricDivisor = volumetricDivisor;
        this.http = builder.baseUrl(config.getBaseUrl()).build();
    }

    @Override public String code() { return CODE; }
    @Override public String displayName() { return "Delhivery"; }
    @Override public boolean external() { return true; }
    @Override public boolean configured() { return config.configured(); }

    @Override
    public ProviderResult getRates(RateRequest r) {
        if (!configured()) {
            return ProviderResult.unavailable(CODE, displayName(), "Delhivery is not connected yet.");
        }
        try {
            if (!serviceable(r.deliveryPincode(), r.cod())) {
                return ProviderResult.unavailable(CODE, displayName(),
                        "Delhivery does not deliver" + (r.cod() ? " cash on delivery" : "") + " to " + r.deliveryPincode() + ".");
            }
            BigDecimal grams = chargeableGrams(r);
            List<ProviderRate> rates = new ArrayList<>();
            for (String[] mode : MODES) {
                JsonNode body = http.get()
                        .uri(u -> u.path("/api/kinko/v1/invoice/charges/.json")
                                .queryParam("md", mode[0])
                                .queryParam("ss", "Delivered")
                                .queryParam("o_pin", r.pickupPincode())
                                .queryParam("d_pin", r.deliveryPincode())
                                .queryParam("cgm", grams.toPlainString())
                                .queryParam("pt", r.cod() ? "COD" : "Pre-paid")
                                .queryParam("cod", r.cod() ? r.declaredValue().toPlainString() : "0")
                                .build())
                        .header("Authorization", "Token " + config.getToken())
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .body(JsonNode.class);
                ProviderRate rate = parse(body, mode);
                if (rate != null) rates.add(rate);
            }
            return ProviderResult.available(CODE, displayName(), rates);
        } catch (RestClientResponseException e) {
            log.warn("Delhivery rates failed: HTTP {}", e.getStatusCode().value());
            return ProviderResult.unavailable(CODE, displayName(), "Delhivery did not return rates for this shipment.");
        } catch (RestClientException e) {
            log.warn("Delhivery rates failed: {}", e.getClass().getSimpleName());
            return ProviderResult.unavailable(CODE, displayName(), "Delhivery could not be reached.");
        }
    }

    private boolean serviceable(String pincode, boolean cod) {
        JsonNode body = http.get()
                .uri(u -> u.path("/c/api/pin-codes/json/").queryParam("filter_codes", pincode).build())
                .header("Authorization", "Token " + config.getToken())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(JsonNode.class);
        JsonNode codes = body == null ? null : body.path("delivery_codes");
        if (codes == null || !codes.isArray() || codes.isEmpty()) return false;
        JsonNode pc = codes.get(0).path("postal_code");
        return !cod || "Y".equalsIgnoreCase(pc.path("cod").asText("N"));
    }

    BigDecimal chargeableGrams(RateRequest r) {
        BigDecimal volumetricKg = r.lengthCm().multiply(r.breadthCm()).multiply(r.heightCm())
                .multiply(BigDecimal.valueOf(r.packageCount()))
                .divide(BigDecimal.valueOf(volumetricDivisor), 3, RoundingMode.HALF_UP);
        return r.actualWeightKg().max(volumetricKg).multiply(BigDecimal.valueOf(1000)).setScale(0, RoundingMode.CEILING);
    }

    static ProviderRate parse(JsonNode body, String[] mode) {
        JsonNode first = body != null && body.isArray() && !body.isEmpty() ? body.get(0) : null;
        if (first == null) return null;
        BigDecimal total = money(first.get("total_amount"));
        BigDecimal gross = money(first.get("gross_amount"));
        if (total == null || total.signum() <= 0) return null;
        List<ChargeLine> lines = new ArrayList<>();
        if (gross != null && gross.signum() > 0 && gross.compareTo(total) <= 0) {
            lines.add(new ChargeLine("FREIGHT", "Freight and charges", ChargeComponent.FREIGHT, gross));
            BigDecimal tax = total.subtract(gross);
            if (tax.signum() > 0) lines.add(new ChargeLine("TAX", "GST (Delhivery)", ChargeComponent.TAX, tax));
        } else {
            lines.add(new ChargeLine("FREIGHT", "Freight (as quoted)", ChargeComponent.FREIGHT, total));
        }
        JsonNode cw = first.get("charged_weight");
        BigDecimal kg = cw == null || cw.isNull() ? null
                : new BigDecimal(cw.asText("0")).divide(BigDecimal.valueOf(1000), 3, RoundingMode.HALF_UP);
        return new ProviderRate(mode[0], "Delhivery " + mode[1], mode[2], null, null, kg, lines, null, null);
    }

    private static BigDecimal money(JsonNode v) {
        if (v == null || v.isNull() || v.asText().isBlank()) return null;
        try {
            return new BigDecimal(v.asText()).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
