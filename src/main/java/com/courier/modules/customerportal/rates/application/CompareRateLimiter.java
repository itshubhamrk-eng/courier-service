package com.courier.modules.customerportal.rates.application;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Caps rate comparisons per portal account (each one calls every external provider). In memory, per
 * instance — enough for one backend container; a shared store comes with horizontal scaling.
 */
@Component
public class CompareRateLimiter {

    static final int MAX_PER_WINDOW = 20;
    static final Duration WINDOW = Duration.ofMinutes(1);

    private final SlidingWindowLimiter limiter = new SlidingWindowLimiter(MAX_PER_WINDOW, WINDOW,
            "Too many rate comparisons. Please wait a minute and try again.");

    public void check(UUID userId) {
        limiter.check(userId);
    }
}
