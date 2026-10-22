package com.courier.modules.kyc.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KycProfileRepository extends JpaRepository<KycProfile, UUID> {

    @Query("select p from KycProfile p where p.companyId = :companyId and p.subjectType = :type and p.subjectId = :subjectId")
    Optional<KycProfile> findBySubject(@Param("companyId") UUID companyId, @Param("type") KycSubjectType type,
                                       @Param("subjectId") UUID subjectId);

    @Query("select p from KycProfile p where p.companyId = :companyId and p.subjectType = :type order by p.updatedAt desc")
    List<KycProfile> findAllOfType(@Param("companyId") UUID companyId, @Param("type") KycSubjectType type);
}
