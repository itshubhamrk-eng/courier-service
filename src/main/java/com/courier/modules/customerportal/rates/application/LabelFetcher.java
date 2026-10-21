package com.courier.modules.customerportal.rates.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Downloads a label PDF from the URL a courier gave us, so the browser never sees (or can swap) that URL.
 * Guarded against being turned into a request forwarder: https only, the provider's own label hosts only,
 * no redirects followed, at most {@value #MAX_BYTES} bytes, and the body must be a PDF.
 */
@Slf4j
@Component
public class LabelFetcher {

    static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F'};

    private final HttpClient http;

    public LabelFetcher() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build());
    }

    LabelFetcher(HttpClient http) {
        this.http = http;
    }

    /** The PDF, or empty when the URL is not allowed, has expired, or did not return a PDF. */
    public Optional<byte[]> fetch(String url, Set<String> allowedHosts) {
        if (!allowed(url, allowedHosts)) {
            log.warn("Label URL refused: host not allowed for this provider");
            return Optional.empty();
        }
        try {
            HttpResponse<InputStream> response = http.send(HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20)).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = response.body()) {
                if (response.statusCode() != 200) {
                    log.info("Label download answered HTTP {}", response.statusCode());
                    return Optional.empty();
                }
                byte[] body = in.readNBytes(MAX_BYTES + 1);
                if (body.length > MAX_BYTES || body.length < PDF_MAGIC.length
                        || !Arrays.equals(Arrays.copyOf(body, PDF_MAGIC.length), PDF_MAGIC)) {
                    log.warn("Label download was not a PDF of acceptable size ({} bytes)", body.length);
                    return Optional.empty();
                }
                return Optional.of(body);
            }
        } catch (IOException e) {
            log.warn("Label download failed: {}", e.getClass().getSimpleName());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    /** https, no credentials or odd port in the URL, and the host equals an entry or ends with a ".suffix" entry. */
    static boolean allowed(String url, Set<String> allowedHosts) {
        if (url == null || allowedHosts == null || allowedHosts.isEmpty()) {
            return false;
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getRawUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)) {
            return false;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return allowedHosts.stream().map(h -> h.toLowerCase(Locale.ROOT))
                .anyMatch(h -> h.startsWith(".") ? host.endsWith(h) && host.length() > h.length() : host.equals(h));
    }
}
