package com.courier.modules.customerportal.rates.application;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/** Caps tracking refreshes and label downloads per portal account: each may call a courier's API. */
@Component
public class ProviderCallLimiter {

    static final int MAX_PER_WINDOW = 30;

    private final SlidingWindowLimiter limiter = new SlidingWindowLimiter(MAX_PER_WINDOW, Duration.ofMinutes(1),
            "Too many tracking or label requests. Please wait a minute and try again.");

    public void check(UUID userId) {
        limiter.check(userId);
    }
}
