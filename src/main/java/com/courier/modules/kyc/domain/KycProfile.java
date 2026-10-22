package com.courier.modules.kyc.domain;

import com.courier.shared.domain.CompanyOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * KYC state of one user, branch or business customer. {@link #email}/{@link #mobile} record which address and
 * number were verified: if the subject's current one differs, it counts as not verified any more.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "kyc_profiles")
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
public class KycProfile extends CompanyOwnedEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false, length = 20, updatable = false)
    private KycSubjectType subjectType;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "subject_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID subjectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private KycStatus status = KycStatus.PENDING;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "mobile", length = 20)
    private String mobile;

    @Column(name = "mobile_verified_at")
    private Instant mobileVerifiedAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
