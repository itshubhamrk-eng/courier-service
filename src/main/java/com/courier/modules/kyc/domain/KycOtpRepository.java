package com.courier.modules.kyc.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface KycOtpRepository extends JpaRepository<KycOtp, UUID> {

    /** Codes sent on a channel since a moment, newest first (rate limits and verification). */
    @Query("select o from KycOtp o where o.companyId = :companyId and o.profileId = :profileId and o.channel = :channel "
            + "and o.createdAt >= :since order by o.createdAt desc")
    List<KycOtp> findRecent(@Param("companyId") UUID companyId, @Param("profileId") UUID profileId,
                            @Param("channel") KycChannel channel, @Param("since") Instant since);
}
