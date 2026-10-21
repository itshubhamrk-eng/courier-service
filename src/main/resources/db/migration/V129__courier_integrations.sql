-- =============================================================================
-- Courier integrations: a company's own accounts with external couriers (Shiprocket,
-- Delhivery, ...), used by the customer portal's rate comparison, booking, tracking
-- and labels. One row per company and provider, created on first save.
--
-- settings_json holds the non-secret fields (pickup location, API user e-mail);
-- secrets_encrypted holds the secret fields (API password / token) as one JSON
-- object, AES-GCM encrypted (EncryptedStringConverter), never returned to a client.
-- A company without a row falls back to the server's environment variables
-- (app.customer-portal.*). Soft delete does not apply: "disconnect" clears the secrets.
-- =============================================================================

CREATE TABLE courier_integrations (
    id                BINARY(16)    NOT NULL,
    company_id        BINARY(16)    NOT NULL,
    provider_code     VARCHAR(40)   NOT NULL,
    enabled           BOOLEAN       NOT NULL DEFAULT FALSE,
    settings_json     VARCHAR(2000) NULL,
    secrets_encrypted VARCHAR(2000) NULL,
    last_tested_at    TIMESTAMP(6)  NULL,
    last_test_ok      BOOLEAN       NULL,
    last_test_message VARCHAR(255)  NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_courier_integration (company_id, provider_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Per-company courier API accounts (secrets AES-GCM encrypted, never returned to a client)';
