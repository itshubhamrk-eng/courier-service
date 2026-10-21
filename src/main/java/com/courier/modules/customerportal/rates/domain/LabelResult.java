package com.courier.modules.customerportal.rates.domain;

/**
 * Where a shipping label can be had: a provider URL the server fetches (never handed to the browser), or
 * not available yet with a message the customer may read.
 */
public record LabelResult(String url, String message) {

    public static LabelResult at(String url) {
        return new LabelResult(url, null);
    }

    public static LabelResult notAvailable(String message) {
        return new LabelResult(null, message);
    }

    public boolean available() {
        return url != null && !url.isBlank();
    }
}
