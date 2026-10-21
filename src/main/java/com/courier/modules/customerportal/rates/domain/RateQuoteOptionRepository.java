package com.courier.modules.customerportal.rates.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RateQuoteOptionRepository extends JpaRepository<RateQuoteOption, UUID> {

    @Query("select o from RateQuoteOption o where o.quoteId = :quoteId and o.companyId = :companyId")
    List<RateQuoteOption> findByQuote(@Param("quoteId") UUID quoteId, @Param("companyId") UUID companyId);

    @Query("select o from RateQuoteOption o where o.id in :ids and o.companyId = :companyId")
    List<RateQuoteOption> findAllWithin(@Param("ids") java.util.Collection<UUID> ids, @Param("companyId") UUID companyId);
}
