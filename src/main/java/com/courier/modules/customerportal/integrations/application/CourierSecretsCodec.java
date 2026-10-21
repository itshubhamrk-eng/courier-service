package com.courier.modules.customerportal.integrations.application;

import com.courier.shared.security.EncryptedStringConverter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads and writes the two JSON maps of a {@code courier_integrations} row: plain settings, and secrets encrypted with
 * the platform's {@link EncryptedStringConverter}. Decrypting by hand (instead of a JPA converter) means a secret that no
 * longer matches {@code SECRETS_ENCRYPTION_KEY} is an exception here, which callers turn into "not connected, enter it
 * again" — it never fails the loading of the row.
 */
@Component
@RequiredArgsConstructor
public class CourierSecretsCodec {

    private static final TypeReference<Map<String, String>> MAP = new TypeReference<>() { };

    private final EncryptedStringConverter encryption;
    private final ObjectMapper json = new ObjectMapper();

    public String encryptSecrets(Map<String, String> secrets) {
        if (secrets.isEmpty()) {
            return null;
        }
        try {
            return encryption.convertToDatabaseColumn(json.writeValueAsString(secrets));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Secrets could not be serialised", e);
        }
    }

    /** @throws IllegalStateException when the ciphertext cannot be decrypted with the current key */
    public Map<String, String> decryptSecrets(String stored) {
        String plain = encryption.convertToEntityAttribute(stored);
        return plain == null ? new LinkedHashMap<>() : read(plain);
    }

    public String writeSettings(Map<String, String> settings) {
        try {
            return settings.isEmpty() ? null : json.writeValueAsString(settings);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Settings could not be serialised", e);
        }
    }

    public Map<String, String> readSettings(String stored) {
        return stored == null || stored.isBlank() ? new LinkedHashMap<>() : read(stored);
    }

    private Map<String, String> read(String text) {
        try {
            return new LinkedHashMap<>(json.readValue(text, MAP));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored courier settings are unreadable", e);
        }
    }
}
