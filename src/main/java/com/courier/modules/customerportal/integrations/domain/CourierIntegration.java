package com.courier.modules.customerportal.integrations.domain;

import com.courier.shared.domain.CompanyOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * A company's own account with one external courier (V129). Non-secret fields live in
 * {@link #settingsJson}; secret fields in {@link #secretsEncrypted} (one JSON object, AES-GCM ciphertext, never
 * returned by the API). No {@code @ToString}: this entity must never print its secrets.
 * Soft delete does not apply (same as {@code CompanyRazorpayConfig}).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "courier_integrations",
        uniqueConstraints = @UniqueConstraint(name = "uk_courier_integration", columnNames = {"company_id", "provider_code"}))
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
public class CourierIntegration extends CompanyOwnedEntity {

    @Column(name = "provider_code", nullable = false, length = 40, updatable = false)
    private String providerCode;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = false;

    @Column(name = "settings_json", length = 2000)
    private String settingsJson;

    /** Ciphertext (see {@code CourierSecretsCodec}); decrypted by hand so an unreadable value cannot break loading the row. */
    @Column(name = "secrets_encrypted", length = 2000)
    private String secretsEncrypted;

    @Column(name = "last_tested_at")
    private Instant lastTestedAt;

    @Column(name = "last_test_ok")
    private Boolean lastTestOk;

    @Column(name = "last_test_message", length = 255)
    private String lastTestMessage;

    /** The courier's logo as a {@code data:image/...;base64,...} URL; set by {@code CourierLogoService}, which checks it. */
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "logo_data_url", columnDefinition = "MEDIUMTEXT")
    private String logoDataUrl;

    /** Secret part of this company's webhook address for this courier (V135); see {@code CourierTrackingWebhookController}. */
    @Column(name = "webhook_token", length = 64)
    private String webhookToken;
}
