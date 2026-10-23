package com.courier.modules.customerportal.rates.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PortalBookingRepository extends JpaRepository<PortalBooking, UUID> {

    @Query("select b from PortalBooking b where b.companyId = :companyId and b.customerId = :customerId "
            + "and b.idempotencyKey = :key")
    Optional<PortalBooking> findByKey(@Param("companyId") UUID companyId, @Param("customerId") UUID customerId,
                                      @Param("key") String key);

    @Query("select b from PortalBooking b where b.id = :id and b.companyId = :companyId and b.customerId = :customerId")
    Optional<PortalBooking> findOwned(@Param("id") UUID id, @Param("companyId") UUID companyId,
                                      @Param("customerId") UUID customerId);

    @Query("select count(b) > 0 from PortalBooking b where b.quoteId = :quoteId and b.companyId = :companyId "
            + "and b.status in :statuses")
    boolean existsForQuote(@Param("quoteId") UUID quoteId, @Param("companyId") UUID companyId,
                           @Param("statuses") Collection<PortalBooking.Status> statuses);

    @Query("select count(b) from PortalBooking b where b.companyId = :companyId and b.customerId = :customerId "
            + "and b.status in :statuses")
    long countForCustomer(@Param("companyId") UUID companyId, @Param("customerId") UUID customerId,
                          @Param("statuses") Collection<PortalBooking.Status> statuses);

    @Query("select b from PortalBooking b where b.companyId = :companyId and b.customerId = :customerId "
            + "order by b.createdAt desc")
    List<PortalBooking> findForCustomer(@Param("companyId") UUID companyId, @Param("customerId") UUID customerId,
                                        org.springframework.data.domain.Pageable page);

    /**
     * One page of the customer's bookings, newest first. {@code provider} "" means every provider; the booking time is
     * within [{@code from}, {@code to}).
     */
    @Query(value = "select b from PortalBooking b where b.companyId = :companyId and b.customerId = :customerId "
            + "and b.status in :statuses and (:provider = '' or b.providerCode = :provider) "
            + "and b.createdAt >= :from and b.createdAt < :to order by b.createdAt desc",
            countQuery = "select count(b) from PortalBooking b where b.companyId = :companyId "
                    + "and b.customerId = :customerId and b.status in :statuses "
                    + "and (:provider = '' or b.providerCode = :provider) and b.createdAt >= :from and b.createdAt < :to")
    org.springframework.data.domain.Page<PortalBooking> findPage(@Param("companyId") UUID companyId,
                                                                 @Param("customerId") UUID customerId,
                                                                 @Param("statuses") Collection<PortalBooking.Status> statuses,
                                                                 @Param("provider") String provider,
                                                                 @Param("from") java.time.Instant from,
                                                                 @Param("to") java.time.Instant to,
                                                                 org.springframework.data.domain.Pageable page);

    /** The providers this customer has booked with, for the history's courier filter. */
    @Query("select distinct b.providerCode from PortalBooking b where b.companyId = :companyId and b.customerId = :customerId")
    List<String> findProviderCodes(@Param("companyId") UUID companyId, @Param("customerId") UUID customerId);
}
