package com.courier.modules.customerportal.rates.domain;

/**
 * One courier source behind the customer portal (OCCPL's own network, or an external courier or
 * aggregator). Booking, tracking, labels and cancellation join this interface in the booking
 * phases. Implementations never throw for a provider failure: they answer
 * {@link ProviderResult#unavailable} with a reason the customer may read (no secrets, no stack traces).
 */
public interface CourierProviderAdapter {

    /** Stable code, stored on quotes: OCCPL_NETWORK, SHIPROCKET, DELHIVERY, BLUEDART, EKART. */
    String code();

    String displayName();

    /** External providers get the configured markup; OCCPL's own network is priced from the customer's rate card. */
    boolean external();

    /** False when credentials/contract are missing: the provider is then shown as "not configured". */
    boolean configured();

    ProviderResult getRates(RateRequest request);
}
