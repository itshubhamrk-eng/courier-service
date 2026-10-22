package com.courier.modules.kyc.domain;

import com.courier.shared.domain.CompanyOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/** The current document of one type for a profile (metadata; the bytes are in {@link KycDocumentFile}). */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "kyc_documents")
@Filter(name = CompanyOwnedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
public class KycDocument extends CompanyOwnedEntity {

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "profile_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID profileId;

    @Column(name = "document_type", nullable = false, length = 40, updatable = false)
    private String documentType;

    @Column(name = "file_name", nullable = false, length = 200)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 40)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "uploaded_by", columnDefinition = "BINARY(16)")
    private UUID uploadedBy;
}
