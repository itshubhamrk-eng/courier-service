package com.courier.modules.kyc.application;

import com.courier.shared.exception.BusinessRuleException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Upload checks: PDF, JPEG or PNG, read from the file's first bytes (never the declared type), at most 5 MB. */
final class KycFiles {

    static final int MAX_BYTES = 5 * 1024 * 1024;

    private KycFiles() {
    }

    static String contentTypeOf(byte[] b) {
        if (b == null || b.length == 0) {
            throw new BusinessRuleException("Choose a file to upload.");
        }
        if (b.length > MAX_BYTES) {
            throw new BusinessRuleException("The file is larger than 5 MB. Upload a smaller scan or photo.");
        }
        if (b.length >= 5 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-') {
            return "application/pdf";
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "image/png";
        }
        throw new BusinessRuleException("Upload a PDF, JPEG or PNG file.");
    }

    /** A safe display name: path parts, control and quote characters removed, at most 150 characters. */
    static String cleanName(String original, String contentType) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"<>]", "").trim();
        if (name.isEmpty()) {
            name = "document" + switch (contentType) {
                case "application/pdf" -> ".pdf";
                case "image/png" -> ".png";
                default -> ".jpg";
            };
        }
        return name.length() <= 150 ? name : name.substring(name.length() - 150);
    }

    static String sha256(byte[] b) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
