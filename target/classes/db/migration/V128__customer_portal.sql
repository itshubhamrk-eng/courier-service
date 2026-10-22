-- =============================================================================
-- Customer portal (e-commerce-business), phase 1: who a portal user is, and the
-- tables the rate comparison and booking phases write. See
-- MEMORY/modules/customer-portal.md.
--
-- 1. users.customer_id links a CUSTOMER-role account to the one business customer
--    it acts for. NULL for every staff account. The portal resolves the customer
--    from this column on every request, never from anything the browser sends.
-- 2. pricing_markup_rules: administrator-managed markup (initially 10 %) for
--    external courier providers. Rows are never edited in place once effective; a
--    change closes the old window and opens a new row, so a quote can always name
--    the rule it was priced with.
-- 3. rate_quotes / rate_quote_options: an immutable snapshot of one comparison —
--    the request, every provider's answer (or why it had none) and the amounts.
-- 4. portal_bookings: one booking attempt per idempotency key, with the provider's
--    reference, AWB and label once known.
-- Money is DECIMAL(14,2); weights DECIMAL(10,3) kilograms.
-- =============================================================================

ALTER TABLE users
    ADD COLUMN customer_id BINARY(16) NULL AFTER hub_id,
    ADD KEY idx_users_company_customer (company_id, customer_id);

CREATE TABLE pricing_markup_rules (
    id              BINARY(16)    NOT NULL,
    company_id      BINARY(16)    NOT NULL,
    -- NULL = every external provider; otherwise one provider code (SHIPROCKET, DELHIVERY ...).
    provider_code   VARCHAR(40)   NULL,
    -- NULL = every customer of the company; otherwise one business customer.
    customer_id     BINARY(16)    NULL,
    markup_enabled  BOOLEAN       NOT NULL DEFAULT TRUE,
    markup_percent  DECIMAL(7,4)  NOT NULL,
    -- Comma-separated charge components the percentage applies to, e.g. FREIGHT,FUEL_SURCHARGE.
    applies_to      VARCHAR(255)  NOT NULL DEFAULT 'FREIGHT',
    effective_from  TIMESTAMP(6)  NOT NULL,
    effective_to    TIMESTAMP(6)  NULL,
    notes           VARCHAR(255)  NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    KEY idx_pmr_company_scope (company_id, provider_code, customer_id, effective_from),
    CONSTRAINT ck_pmr_percent CHECK (markup_percent >= 0 AND markup_percent <= 100)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Markup on external courier rates; one row per effective window';

CREATE TABLE rate_quotes (
    id                    BINARY(16)    NOT NULL,
    company_id            BINARY(16)    NOT NULL,
    customer_id           BINARY(16)    NOT NULL,
    requested_by          BINARY(16)    NOT NULL,
    pickup_pincode        VARCHAR(6)    NOT NULL,
    delivery_pincode      VARCHAR(6)    NOT NULL,
    -- Pickup/delivery contacts and addresses, packages and dimensions as submitted.
    request_json          JSON          NOT NULL,
    actual_weight_kg      DECIMAL(10,3) NOT NULL,
    volumetric_weight_kg  DECIMAL(10,3) NOT NULL,
    package_count         INT           NOT NULL,
    declared_value        DECIMAL(14,2) NOT NULL,
    payment_mode          VARCHAR(10)   NOT NULL,
    status                VARCHAR(20)   NOT NULL DEFAULT 'OPEN',
    expires_at            TIMESTAMP(6)  NOT NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    KEY idx_rq_company_customer (company_id, customer_id, created_at),
    CONSTRAINT fk_rq_customer FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'One customer rate comparison: the request as submitted';

CREATE TABLE rate_quote_options (
    id                    BINARY(16)    NOT NULL,
    company_id            BINARY(16)    NOT NULL,
    quote_id              BINARY(16)    NOT NULL,
    provider_code         VARCHAR(40)   NOT NULL,
    service_code          VARCHAR(80)   NULL,
    service_name          VARCHAR(150)  NULL,
    transport_mode        VARCHAR(20)   NULL,
    -- AVAILABLE, or UNAVAILABLE with a reason (not configured, not serviceable, provider error).
    availability          VARCHAR(20)   NOT NULL,
    unavailable_reason    VARCHAR(255)  NULL,
    eta_min_days          INT           NULL,
    eta_max_days          INT           NULL,
    chargeable_weight_kg  DECIMAL(10,3) NULL,
    base_freight          DECIMAL(14,2) NULL,
    other_charges         DECIMAL(14,2) NULL,
    markup_rule_id        BINARY(16)    NULL,
    markup_percent        DECIMAL(7,4)  NULL,
    markup_base           DECIMAL(14,2) NULL,
    markup_amount         DECIMAL(14,2) NULL,
    tax_amount            DECIMAL(14,2) NULL,
    total_amount          DECIMAL(14,2) NULL,
    currency              VARCHAR(3)    NOT NULL DEFAULT 'INR',
    -- Every charge line as the provider returned it, plus the markup and tax lines.
    breakdown_json        JSON          NULL,
    provider_quote_ref    VARCHAR(120)  NULL,
    valid_until           TIMESTAMP(6)  NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    KEY idx_rqo_quote (quote_id),
    KEY idx_rqo_company (company_id),
    CONSTRAINT fk_rqo_quote FOREIGN KEY (quote_id) REFERENCES rate_quotes (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'One provider service in a rate comparison, priced and frozen';

CREATE TABLE portal_bookings (
    id                  BINARY(16)    NOT NULL,
    company_id          BINARY(16)    NOT NULL,
    customer_id         BINARY(16)    NOT NULL,
    quote_id            BINARY(16)    NOT NULL,
    quote_option_id     BINARY(16)    NOT NULL,
    -- Sent by the portal per "Confirm booking" click; a repeat returns the same booking.
    idempotency_key     VARCHAR(80)   NOT NULL,
    request_hash        VARCHAR(64)   NOT NULL,
    provider_code       VARCHAR(40)   NOT NULL,
    -- PENDING -> SUBMITTING -> BOOKED | FAILED | OUTCOME_UNKNOWN; OUTCOME_UNKNOWN is never
    -- retried automatically (the provider may have accepted it).
    status              VARCHAR(20)   NOT NULL,
    total_amount        DECIMAL(14,2) NOT NULL,
    currency            VARCHAR(3)    NOT NULL DEFAULT 'INR',
    shipment_id         BINARY(16)    NULL,
    provider_order_ref  VARCHAR(120)  NULL,
    awb_number          VARCHAR(60)   NULL,
    label_url           VARCHAR(1024) NULL,
    failure_code        VARCHAR(60)   NULL,
    failure_message     VARCHAR(500)  NULL,
    submitted_at        TIMESTAMP(6)  NULL,
    booked_at           TIMESTAMP(6)  NULL,

    created_at TIMESTAMP(6) NOT NULL, created_by BINARY(16) NULL,
    updated_at TIMESTAMP(6) NOT NULL, updated_by BINARY(16) NULL,
    deleted    BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL, deleted_by BINARY(16) NULL,
    version    BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uk_pb_idempotency (company_id, customer_id, idempotency_key),
    KEY idx_pb_company_customer (company_id, customer_id, created_at),
    KEY idx_pb_awb (company_id, awb_number),
    CONSTRAINT fk_pb_quote FOREIGN KEY (quote_id) REFERENCES rate_quotes (id) ON DELETE RESTRICT,
    CONSTRAINT fk_pb_option FOREIGN KEY (quote_option_id) REFERENCES rate_quote_options (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Customer portal booking attempts; idempotent per key';
