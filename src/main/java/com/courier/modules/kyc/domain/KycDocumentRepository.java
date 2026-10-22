package com.courier.modules.kyc.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KycDocumentRepository extends JpaRepository<KycDocument, UUID> {

    @Query("select d from KycDocument d where d.companyId = :companyId and d.profileId = :profileId order by d.createdAt")
    List<KycDocument> findAllOfProfile(@Param("companyId") UUID companyId, @Param("profileId") UUID profileId);

    @Query("select d from KycDocument d where d.companyId = :companyId and d.profileId = :profileId and d.documentType = :type")
    Optional<KycDocument> findOfType(@Param("companyId") UUID companyId, @Param("profileId") UUID profileId,
                                     @Param("type") String type);
}
