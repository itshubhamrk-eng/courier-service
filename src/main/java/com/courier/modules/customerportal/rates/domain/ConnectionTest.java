package com.courier.modules.customerportal.rates.domain;

/** The answer of a courier to "are these credentials accepted?". The message is safe to show a company administrator. */
public record ConnectionTest(boolean ok, String message) {

    public static ConnectionTest ok(String message) {
        return new ConnectionTest(true, message);
    }

    public static ConnectionTest failed(String message) {
        return new ConnectionTest(false, message);
    }
}
