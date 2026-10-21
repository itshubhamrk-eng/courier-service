package com.courier.modules.customerportal.rates.domain;

import java.util.List;

/**
 * A shipment's progress as its provider reports it right now, newest event first. Not stored: asked
 * again on every view. {@code available=false} carries a message the customer may read.
 */
public record TrackingResult(boolean available, String status, String trackingNumber, String currentLocation,
                             List<TrackingEvent> events, String message) {

    public static TrackingResult of(String status, String trackingNumber, String currentLocation,
                                    List<TrackingEvent> events) {
        return new TrackingResult(true, status, trackingNumber, currentLocation, List.copyOf(events), null);
    }

    public static TrackingResult unavailable(String message) {
        return new TrackingResult(false, null, null, null, List.of(), message);
    }
}
