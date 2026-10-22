package com.courier.modules.kyc.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/** The bytes of a {@link KycDocument}; read only when the file itself is downloaded. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "kyc_document_files")
public class KycDocumentFile {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "document_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID documentId;

    @JdbcTypeCode(SqlTypes.LONGVARBINARY)
    @Column(name = "content", columnDefinition = "LONGBLOB", nullable = false)
    private byte[] content;

    public KycDocumentFile(UUID documentId, byte[] content) {
        this.documentId = documentId;
        this.content = content;
    }
}
