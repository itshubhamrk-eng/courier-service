package com.courier.modules.customerportal.integrations.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourierIntegrationRepository extends JpaRepository<CourierIntegration, UUID> {

    @Query("select i from CourierIntegration i where i.companyId = :companyId and i.providerCode = :code")
    Optional<CourierIntegration> findByProvider(@Param("companyId") UUID companyId, @Param("code") String code);

    @Query("select i from CourierIntegration i where i.companyId = :companyId")
    List<CourierIntegration> findAllOfCompany(@Param("companyId") UUID companyId);

    /** The integration a webhook address belongs to. Webhooks arrive with no company context: matched on the secret only. */
    @Query("select i from CourierIntegration i where i.webhookToken = :token")
    Optional<CourierIntegration> findByWebhookTokenAcrossCompanies(@Param("token") String token);
}
