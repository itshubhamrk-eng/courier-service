package com.courier.modules.customerportal.rates.infrastructure;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/** Reads the timestamps courier APIs send. Times without an offset are Indian time. Unreadable -> null. */
public final class ProviderTimes {

    static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    private static final List<DateTimeFormatter> LOCAL = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT),
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss", Locale.ROOT),
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH));

    private ProviderTimes() {
    }

    public static Instant parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        try {
            return OffsetDateTime.parse(s).toInstant();
        } catch (DateTimeParseException ignored) {
            // no offset: try the local forms
        }
        for (DateTimeFormatter f : LOCAL) {
            try {
                return LocalDateTime.parse(s, f).atZone(INDIA).toInstant();
            } catch (DateTimeParseException ignored) {
                // next form
            }
        }
        return null;
    }

    /** OUT_FOR_DELIVERY -> "Out for delivery". */
    static String label(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String s = code.trim().replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
