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
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Markup on external courier rates for one scope (all providers or one; all customers or one) and one
 * effective window (V128). Never edited once in force: a change closes this window and adds a new row.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "pricing_markup_rules")
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@SQLRestriction("deleted = false")
public class PricingMarkupRule extends CompanyOwnedEntity {

    @Column(name = "provider_code", length = 40)
    private String providerCode;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "customer_id", columnDefinition = "BINARY(16)")
    private UUID customerId;

    @Column(name = "markup_enabled", nullable = false)
    private boolean markupEnabled;

    @Column(name = "markup_percent", nullable = false, precision = 7, scale = 4)
    private BigDecimal markupPercent;

    @Column(name = "applies_to", nullable = false, length = 255)
    private String appliesTo;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    @Column(name = "notes", length = 255)
    private String notes;

    public Set<ChargeComponent> components() {
        if (appliesTo == null || appliesTo.isBlank()) {
            return EnumSet.of(ChargeComponent.FREIGHT);
        }
        Set<ChargeComponent> set = Arrays.stream(appliesTo.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).map(ChargeComponent::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(ChargeComponent.class)));
        set.remove(ChargeComponent.TAX);
        return set;
    }

    public boolean coversInstant(Instant at) {
        return !effectiveFrom.isAfter(at) && (effectiveTo == null || effectiveTo.isAfter(at));
    }

    /** Most specific first: customer + provider, customer, provider, everything. */
    public int specificity() {
        return (customerId != null ? 2 : 0) + (providerCode != null ? 1 : 0);
    }
}
