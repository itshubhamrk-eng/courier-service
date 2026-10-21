package com.courier.modules.customerportal.rates.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RateQuoteRepository extends JpaRepository<RateQuote, UUID> {

    /** A quote only ever loads for the customer it belongs to. */
    @Query("select q from RateQuote q where q.id = :id and q.companyId = :companyId and q.customerId = :customerId")
    Optional<RateQuote> findOwned(@Param("id") UUID id, @Param("companyId") UUID companyId,
                                  @Param("customerId") UUID customerId);
}
