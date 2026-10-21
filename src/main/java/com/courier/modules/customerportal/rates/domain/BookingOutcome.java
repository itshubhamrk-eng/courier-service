package com.courier.modules.customerportal.rates.domain;

/**
 * How a booking attempt ended. UNKNOWN means the provider may have accepted it (timeout, connection cut,
 * half-finished multi-step booking): it is never retried automatically — operations reconcile it.
 */
public record BookingOutcome(Result result, String providerOrderRef, String awbNumber, String labelUrl,
                             String failureCode, String message) {

    public enum Result { BOOKED, REJECTED, UNKNOWN }

    public static BookingOutcome booked(String orderRef, String awb, String labelUrl, String message) {
        return new BookingOutcome(Result.BOOKED, orderRef, awb, labelUrl, null, message);
    }

    public static BookingOutcome rejected(String code, String message) {
        return new BookingOutcome(Result.REJECTED, null, null, null, code, message);
    }

    public static BookingOutcome unknown(String orderRef, String message) {
        return new BookingOutcome(Result.UNKNOWN, orderRef, null, null, "OUTCOME_UNKNOWN", message);
    }
}
