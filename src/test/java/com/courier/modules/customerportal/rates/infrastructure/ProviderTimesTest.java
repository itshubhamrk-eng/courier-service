package com.courier.modules.customerportal.rates.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderTimesTest {

    @Test
    @DisplayName("times without an offset are Indian time; offsets are honoured; junk is null")
    void parses() {
        assertThat(ProviderTimes.parse("2026-10-21 10:30:00")).isEqualTo(Instant.parse("2026-10-21T05:00:00Z"));
        assertThat(ProviderTimes.parse("2026-10-21T10:30:00.123")).isEqualTo(Instant.parse("2026-10-21T05:00:00.123Z"));
        assertThat(ProviderTimes.parse("2026-10-21T10:30:00+00:00")).isEqualTo(Instant.parse("2026-10-21T10:30:00Z"));
        assertThat(ProviderTimes.parse("yesterday")).isNull();
        assertThat(ProviderTimes.parse(null)).isNull();
    }

    @Test
    void labels() {
        assertThat(ProviderTimes.label("OUT_FOR_DELIVERY")).isEqualTo("Out for delivery");
        assertThat(ProviderTimes.label(" ")).isNull();
    }
}
