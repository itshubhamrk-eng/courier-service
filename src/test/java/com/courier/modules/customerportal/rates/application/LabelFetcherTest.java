package com.courier.modules.customerportal.rates.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LabelFetcherTest {

    private static final Set<String> HOSTS = Set.of(".amazonaws.com", "labels.delhivery.com");

    @Test
    @DisplayName("only https URLs on the provider's label hosts are fetched")
    void allowList() {
        assertThat(LabelFetcher.allowed("https://bucket.s3.ap-south-1.amazonaws.com/a.pdf", HOSTS)).isTrue();
        assertThat(LabelFetcher.allowed("https://labels.delhivery.com/a.pdf", HOSTS)).isTrue();

        assertThat(LabelFetcher.allowed("http://bucket.s3.amazonaws.com/a.pdf", HOSTS)).isFalse();
        assertThat(LabelFetcher.allowed("https://amazonaws.com.evil.test/a.pdf", HOSTS)).isFalse();
        assertThat(LabelFetcher.allowed("https://evilamazonaws.com/a.pdf", HOSTS)).isFalse();
        assertThat(LabelFetcher.allowed("https://x.labels.delhivery.com/a.pdf", HOSTS)).isFalse();
        assertThat(LabelFetcher.allowed("https://user:pw@bucket.amazonaws.com/a.pdf", HOSTS)).isFalse();
        assertThat(LabelFetcher.allowed("https://bucket.amazonaws.com:8443/a.pdf", HOSTS)).isFalse();
        assertThat(LabelFetcher.allowed("https://169.254.169.254/latest/meta-data", HOSTS)).isFalse();
        assertThat(LabelFetcher.allowed("not a url", HOSTS)).isFalse();
        assertThat(LabelFetcher.allowed("https://bucket.amazonaws.com/a.pdf", Set.of())).isFalse();
    }
}
