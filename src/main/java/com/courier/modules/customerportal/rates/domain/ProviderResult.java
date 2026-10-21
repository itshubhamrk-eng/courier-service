package com.courier.modules.customerportal.rates.domain;

import java.util.List;

/** A provider's answer: services with prices, or why there are none. */
public record ProviderResult(String providerCode, String providerName, boolean available, String reason,
                             List<ProviderRate> rates) {

    public static ProviderResult available(String code, String name, List<ProviderRate> rates) {
        return rates.isEmpty()
                ? unavailable(code, name, "No service for this shipment.")
                : new ProviderResult(code, name, true, null, List.copyOf(rates));
    }

    public static ProviderResult unavailable(String code, String name, String reason) {
        return new ProviderResult(code, name, false, reason, List.of());
    }
}
