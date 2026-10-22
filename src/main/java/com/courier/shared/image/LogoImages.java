package com.courier.shared.image;

import com.courier.shared.exception.BusinessRuleException;

import java.util.Base64;

/**
 * Checks an uploaded logo and turns it into a {@code data:} URL. The type comes from the file's first bytes, never from the
 * upload's declared type, and only raster images are accepted (an SVG can carry script).
 */
public final class LogoImages {

    public static final int MAX_BYTES = 150 * 1024;

    private LogoImages() {
    }

    public static String toDataUrl(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new BusinessRuleException("Choose a logo file.");
        }
        if (bytes.length > MAX_BYTES) {
            throw new BusinessRuleException("The logo is larger than " + (MAX_BYTES / 1024) + " KB. Use a smaller image.");
        }
        String type = sniff(bytes);
        if (type == null) {
            throw new BusinessRuleException("The logo must be a PNG, JPEG or WebP image.");
        }
        return "data:" + type + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    /** The image type from the file's signature, or null when it is not one of the accepted types. */
    static String sniff(byte[] b) {
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "image/png";
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        return null;
    }
}
