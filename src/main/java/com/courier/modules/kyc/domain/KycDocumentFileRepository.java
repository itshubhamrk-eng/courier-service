package com.courier.modules.kyc.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Always reached through a {@link KycDocument} already checked to belong to the caller's company. */
public interface KycDocumentFileRepository extends JpaRepository<KycDocumentFile, UUID> {
}
