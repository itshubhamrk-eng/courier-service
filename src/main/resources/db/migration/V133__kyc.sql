-- =============================================================================
-- KYC: users, branches and business customers complete their own KYC after signing in —
-- upload the documents their type needs and verify their e-mail and mobile with a one-time
-- code. KYC is complete (auto-approved) once every required document is uploaded and both are
-- verified. A company administrator can view every profile and its documents.
--
-- kyc_profiles        one per (company, subject type, subject id); created on first visit.
-- kyc_documents       one current document per (profile, document type); metadata only.
-- kyc_document_files  the bytes, in their own table so listing documents never loads them.
-- kyc_otps            one-time codes (BCrypt hash, 10 min, 5 attempts).
-- =============================================================================

CREATE TABLE kyc_profiles (
    id                 BINARY(16)   NOT NULL,
    company_id         BINARY(16)   NOT NULL,
    subject_type       VARCHAR(20)  NOT NULL,
    subject_id         BINARY(16)   NOT NULL,
    status             VARCHAR(20)  NOT NULL,
    email              VARCHAR(255) NULL,
    email_verified_at  TIMESTAMP(6) NULL,
    mobile             VARCHAR(20)  NULL,
    mobile_verified_at TIMESTAMP(6) NULL,
    completed_at       TIMESTAMP(6) NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_kyc_profile_subject (company_id, subject_type, subject_id),
    KEY idx_kyc_profile_status (company_id, subject_type, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'KYC state of a user, branch or business customer';

CREATE TABLE kyc_documents (
    id            BINARY(16)   NOT NULL,
    company_id    BINARY(16)   NOT NULL,
    profile_id    BINARY(16)   NOT NULL,
    document_type VARCHAR(40)  NOT NULL,
    file_name     VARCHAR(200) NOT NULL,
    content_type  VARCHAR(40)  NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    sha256        VARCHAR(64)  NOT NULL,
    uploaded_by   BINARY(16)   NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_kyc_document_type (profile_id, document_type),
    CONSTRAINT fk_kyc_document_profile FOREIGN KEY (profile_id) REFERENCES kyc_profiles (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Current KYC document per profile and type (metadata)';

CREATE TABLE kyc_document_files (
    document_id BINARY(16) NOT NULL,
    content     LONGBLOB   NOT NULL,
    PRIMARY KEY (document_id),
    CONSTRAINT fk_kyc_file_document FOREIGN KEY (document_id) REFERENCES kyc_documents (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'KYC document bytes (PDF/JPEG/PNG, at most 5 MB)';

CREATE TABLE kyc_otps (
    id          BINARY(16)   NOT NULL,
    company_id  BINARY(16)   NOT NULL,
    profile_id  BINARY(16)   NOT NULL,
    channel     VARCHAR(10)  NOT NULL,
    target      VARCHAR(255) NOT NULL,
    code_hash   VARCHAR(100) NOT NULL,
    expires_at  TIMESTAMP(6) NOT NULL,
    attempts    INT          NOT NULL DEFAULT 0,
    consumed_at TIMESTAMP(6) NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    KEY idx_kyc_otp_profile (profile_id, channel, created_at),
    CONSTRAINT fk_kyc_otp_profile FOREIGN KEY (profile_id) REFERENCES kyc_profiles (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'KYC e-mail / mobile one-time codes (hashed)';
