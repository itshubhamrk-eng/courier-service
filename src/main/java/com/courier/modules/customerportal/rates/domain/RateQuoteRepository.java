package com.courier.modules.customerportal.rates.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RateQuoteRepository extends JpaRepository<RateQuote, UUID> {

    /** A quote only ever loads for the customer it belongs to. */
    @Query("select q from RateQuote q where q.id = :id and q.companyId = :companyId and q.customerId = :customerId")
    Optional<RateQuote> findOwned(@Param("id") UUID id, @Param("companyId") UUID companyId,
                                  @Param("customerId") UUID customerId);

    /** Same, row-locked: serialises concurrent bookings of one quote. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from RateQuote q where q.id = :id and q.companyId = :companyId and q.customerId = :customerId")
    Optional<RateQuote> findOwnedForUpdate(@Param("id") UUID id, @Param("companyId") UUID companyId,
                                           @Param("customerId") UUID customerId);

    @Query("select q from RateQuote q where q.id in :ids and q.companyId = :companyId and q.customerId = :customerId")
    java.util.List<RateQuote> findAllOwned(@Param("ids") java.util.Collection<UUID> ids, @Param("companyId") UUID companyId,
                                           @Param("customerId") UUID customerId);
}
