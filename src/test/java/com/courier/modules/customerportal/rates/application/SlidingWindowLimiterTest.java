package com.courier.modules.customerportal.rates.application;

import com.courier.shared.exception.ApiException;
import com.courier.shared.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlidingWindowLimiterTest {

    @Test
    @DisplayName("allows up to the cap per account, then answers 429; other accounts are unaffected")
    void capsPerAccount() {
        SlidingWindowLimiter limiter = new SlidingWindowLimiter(3, Duration.ofMinutes(1), "slow down");
        UUID a = UUID.randomUUID();
        for (int i = 0; i < 3; i++) limiter.check(a);

        assertThatThrownBy(() -> limiter.check(a)).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED))
                .hasMessage("slow down");
        assertThatCode(() -> limiter.check(UUID.randomUUID())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("calls older than the window stop counting")
    void windowSlides() throws InterruptedException {
        SlidingWindowLimiter limiter = new SlidingWindowLimiter(1, Duration.ofMillis(50), "slow down");
        UUID a = UUID.randomUUID();
        limiter.check(a);
        Thread.sleep(80);

        assertThatCode(() -> limiter.check(a)).doesNotThrowAnyException();
    }
}
