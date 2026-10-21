package com.courier.modules.customerportal.rates.application;

import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ErrorCode;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** At most {@code max} calls per portal account per window. In memory, per instance. */
class SlidingWindowLimiter {

    private final int max;
    private final Duration window;
    private final String message;
    private final Map<UUID, Deque<Instant>> calls = new ConcurrentHashMap<>();

    SlidingWindowLimiter(int max, Duration window, String message) {
        this.max = max;
        this.window = window;
        this.message = message;
    }

    void check(UUID userId) {
        Instant now = Instant.now();
        Deque<Instant> q = calls.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst().isBefore(now.minus(window))) {
                q.pollFirst();
            }
            if (q.size() >= max) {
                throw new BusinessRuleException(ErrorCode.RATE_LIMIT_EXCEEDED, message);
            }
            q.addLast(now);
        }
    }
}
