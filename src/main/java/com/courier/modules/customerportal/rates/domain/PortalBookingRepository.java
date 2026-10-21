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
}
