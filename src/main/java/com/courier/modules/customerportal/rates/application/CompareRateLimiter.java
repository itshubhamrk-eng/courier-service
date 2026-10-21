package com.courier.modules.customerportal.rates.application;

import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caps rate comparisons per portal account (each one calls every external provider). In memory, per
 * instance — enough for one backend container; a shared store comes with horizontal scaling.
 */
@Component
public class CompareRateLimiter {

    static final int MAX_PER_WINDOW = 20;
    static final Duration WINDOW = Duration.ofMinutes(1);

    private final Map<UUID, Deque<Instant>> calls = new ConcurrentHashMap<>();

    public void check(UUID userId) {
        Instant now = Instant.now();
        Deque<Instant> q = calls.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst().isBefore(now.minus(WINDOW))) {
                q.pollFirst();
            }
            if (q.size() >= MAX_PER_WINDOW) {
                throw new BusinessRuleException(ErrorCode.RATE_LIMIT_EXCEEDED,
                        "Too many rate comparisons. Please wait a minute and try again.");
            }
            q.addLast(now);
        }
    }
}
