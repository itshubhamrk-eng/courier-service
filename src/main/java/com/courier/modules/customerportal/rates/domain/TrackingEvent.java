package com.courier.modules.customerportal.rates.domain;

import java.time.Instant;

/** One scan or status change as the provider reported it. {@code at} is null when the provider's time could not be read. */
public record TrackingEvent(Instant at, String status, String location, String description) {
}
