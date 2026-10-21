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
import java.util.Map;
import java.util.UUID;

/** One rate comparison, as submitted (V128). Immutable apart from its status. */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "rate_quotes")
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class RateQuote extends CompanyOwnedEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "customer_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID customerId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "requested_by", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID requestedBy;

    @Column(name = "pickup_pincode", nullable = false, length = 6, updatable = false)
    private String pickupPincode;

    @Column(name = "delivery_pincode", nullable = false, length = 6, updatable = false)
    private String deliveryPincode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_json", columnDefinition = "JSON", nullable = false, updatable = false)
    private Map<String, Object> request;

    @Column(name = "actual_weight_kg", nullable = false, precision = 10, scale = 3, updatable = false)
    private BigDecimal actualWeightKg;

    @Column(name = "volumetric_weight_kg", nullable = false, precision = 10, scale = 3, updatable = false)
    private BigDecimal volumetricWeightKg;

    @Column(name = "package_count", nullable = false, updatable = false)
    private int packageCount;

    @Column(name = "declared_value", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal declaredValue;

    @Column(name = "payment_mode", nullable = false, length = 10, updatable = false)
    private String paymentMode;

    /** OPEN, BOOKED or EXPIRED (expiry is also judged from expiresAt at read time). */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;
}
