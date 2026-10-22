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

/** A one-time code sent to an e-mail address or mobile number. Only its BCrypt hash is stored. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "kyc_otps")
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
public class KycOtp extends CompanyOwnedEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "profile_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID profileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 10, updatable = false)
    private KycChannel channel;

    @Column(name = "target", nullable = false, length = 255, updatable = false)
    private String target;

    @Column(name = "code_hash", nullable = false, length = 100, updatable = false)
    private String codeHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "consumed_at")
    private Instant consumedAt;
}
