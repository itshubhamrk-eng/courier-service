package com.courier.modules.customerportal.rates.domain;

/**
 * One courier source behind the customer portal (OCCPL's own network, or an external courier or
 * aggregator). Implementations never throw for a provider failure: rates answer
 * {@link ProviderResult#unavailable}, bookings a {@link BookingOutcome}, each with a reason the customer
 * may read (no secrets, no stack traces). Tracking, labels and cancellation join in the next phase.
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

    /**
     * Books one service of a frozen quote. Must classify honestly: REJECTED only when the provider certainly
     * did not accept it; anything in doubt is UNKNOWN.
     */
    default BookingOutcome book(BookingRequest request) {
        return BookingOutcome.rejected("NOT_SUPPORTED", displayName() + " bookings are not available yet.");
    }
}
