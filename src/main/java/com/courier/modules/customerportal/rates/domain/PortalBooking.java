package com.courier.modules.customerportal.rates.domain;

import com.courier.shared.domain.CompanyOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/** One booking attempt from the customer portal (V128), unique per (company, customer, idempotency key). */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "portal_bookings")
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class PortalBooking extends CompanyOwnedEntity {

    public enum Status {
        PENDING, SUBMITTING, BOOKED, FAILED, OUTCOME_UNKNOWN;

        /** Statuses that block another booking of the same quote. */
        public static final Set<Status> ACTIVE = EnumSet.of(PENDING, SUBMITTING, BOOKED, OUTCOME_UNKNOWN);
    }

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "customer_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID customerId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "quote_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID quoteId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "quote_option_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID quoteOptionId;

    @Column(name = "idempotency_key", nullable = false, length = 80, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64, updatable = false)
    private String requestHash;

    @Column(name = "provider_code", nullable = false, length = 40, updatable = false)
    private String providerCode;

    @Column(name = "status", nullable = false, length = 20)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private Status status;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal totalAmount;

    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private String currency;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "shipment_id", columnDefinition = "BINARY(16)")
    private UUID shipmentId;

    @Column(name = "provider_order_ref", length = 120) private String providerOrderRef;
    @Column(name = "awb_number", length = 60) private String awbNumber;
    @Column(name = "label_url", length = 1024) private String labelUrl;
    @Column(name = "failure_code", length = 60) private String failureCode;
    @Column(name = "failure_message", length = 500) private String failureMessage;
    @Column(name = "submitted_at") private Instant submittedAt;
    @Column(name = "booked_at") private Instant bookedAt;
}
