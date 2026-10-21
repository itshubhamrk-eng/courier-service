package com.courier.modules.customerportal.rates.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PricingMarkupRuleRepository extends JpaRepository<PricingMarkupRule, UUID> {

    @Query("select r from PricingMarkupRule r where r.companyId = :companyId order by r.effectiveFrom desc")
    List<PricingMarkupRule> findAllWithinCompany(@Param("companyId") UUID companyId);

    /** Rules that could apply to one quote: same company, matching or blank provider/customer. */
    @Query("select r from PricingMarkupRule r where r.companyId = :companyId "
            + "and (r.providerCode is null or r.providerCode = :providerCode) "
            + "and (r.customerId is null or r.customerId = :customerId)")
    List<PricingMarkupRule> findCandidates(@Param("companyId") UUID companyId,
                                           @Param("providerCode") String providerCode,
                                           @Param("customerId") UUID customerId);
}
