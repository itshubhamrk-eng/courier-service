package com.courier.modules.customerportal.rates.infrastructure;

import com.courier.modules.customerportal.rates.domain.CourierProviderAdapter;
import com.courier.modules.customerportal.rates.domain.ProviderResult;
import com.courier.modules.customerportal.rates.domain.RateRequest;

/**
 * A courier OCCPL intends to offer but has no API access to yet (Blue Dart, Ekart: a contract and API
 * documentation come first). Always "not available" — never a made-up price.
 */
public class NotConfiguredAdapter implements CourierProviderAdapter {

    private final String code;
    private final String name;

    public NotConfiguredAdapter(String code, String name) {
        this.code = code;
        this.name = name;
    }

    @Override public String code() { return code; }
    @Override public String displayName() { return name; }
    @Override public boolean external() { return true; }
    @Override public boolean configured() { return false; }

    @Override
    public ProviderResult getRates(RateRequest request) {
        return ProviderResult.unavailable(code, name, name + " is not connected yet.");
    }
}
