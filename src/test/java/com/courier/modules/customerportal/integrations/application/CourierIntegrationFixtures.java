package com.courier.modules.customerportal.integrations.application;

import com.courier.modules.customerportal.integrations.domain.CourierIntegration;
import com.courier.modules.customerportal.integrations.domain.CourierIntegrationRepository;
import com.courier.shared.security.EncryptedStringConverter;
import com.courier.shared.security.SecretsEncryptionProperties;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** A real codec with a random key and an in-memory repository, shared by the integration tests. */
final class CourierIntegrationFixtures {

    final List<CourierIntegration> rows = new ArrayList<>();
    final CourierIntegrationRepository repository = mock(CourierIntegrationRepository.class);
    final CourierSecretsCodec codec;

    CourierIntegrationFixtures() {
        this(randomKey());
    }

    CourierIntegrationFixtures(String key) {
        SecretsEncryptionProperties p = new SecretsEncryptionProperties();
        p.setEncryptionKey(key);
        codec = new CourierSecretsCodec(new EncryptedStringConverter(p));
        when(repository.findByProvider(any(), any())).thenAnswer(i -> rows.stream()
                .filter(r -> r.getCompanyId().equals(i.getArgument(0)) && r.getProviderCode().equals(i.getArgument(1))).findFirst());
        when(repository.save(any())).thenAnswer(i -> {
            CourierIntegration row = i.getArgument(0);
            if (row.getId() == null) {
                row.setId(UUID.randomUUID());
            }
            if (row.getCompanyId() == null) {
                row.setCompanyId(com.courier.shared.company.CompanyContext.requireCompanyId());
            }
            if (rows.stream().noneMatch(r -> r == row)) {
                rows.add(row);
            }
            return row;
        });
    }

    static String randomKey() {
        byte[] k = new byte[32];
        new SecureRandom().nextBytes(k);
        return Base64.getEncoder().encodeToString(k);
    }

    CourierIntegration saved(UUID company, String provider, boolean enabled, java.util.Map<String, String> settings,
                             java.util.Map<String, String> secrets) {
        CourierIntegration row = new CourierIntegration();
        row.setId(UUID.randomUUID());
        row.setCompanyId(company);
        row.setProviderCode(provider);
        row.setEnabled(enabled);
        row.setSettingsJson(codec.writeSettings(settings));
        row.setSecretsEncrypted(codec.encryptSecrets(secrets));
        rows.add(row);
        return row;
    }

}
