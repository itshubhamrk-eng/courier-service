package com.courier.modules.customerportal.application;

import com.courier.modules.shipment.application.ShipmentPortalQueries;
import com.courier.modules.shipment.application.ShipmentPortalQueries.PortalShipment;
import com.courier.modules.shipment.domain.ShipmentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Counts and recent shipments for the signed-in customer's dashboard. */
@Service
@RequiredArgsConstructor
public class CustomerPortalDashboardService {

    static final Set<ShipmentStatus> BOOKED = EnumSet.of(ShipmentStatus.BOOKED, ShipmentStatus.READY_FOR_MANIFEST);
    static final Set<ShipmentStatus> IN_TRANSIT = EnumSet.of(ShipmentStatus.MANIFEST_CREATED,
            ShipmentStatus.DISPATCHED, ShipmentStatus.IN_SCAN, ShipmentStatus.OUT_FOR_DELIVERY);
    private static final int RECENT = 5;

    private final CurrentCustomerResolver resolver;
    private final ShipmentPortalQueries shipments;

    @Transactional(readOnly = true)
    public Dashboard dashboard() {
        ActingCustomer acting = resolver.require();
        Map<ShipmentStatus, Long> counts = shipments.statusCounts(acting.companyId(), acting.customerId());
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        return new Dashboard(
                total,
                // Portal bookings that are not yet a shipment (submitting / outcome unknown) arrive with
                // the booking phase; until then every booking is a shipment and this is genuinely 0.
                0L,
                sum(counts, BOOKED),
                sum(counts, IN_TRANSIT),
                counts.get(ShipmentStatus.DELIVERED),
                counts.get(ShipmentStatus.RETURNED),
                counts.get(ShipmentStatus.CANCELLED),
                shipments.recent(acting.companyId(), acting.customerId(), RECENT));
    }

    private static long sum(Map<ShipmentStatus, Long> counts, Set<ShipmentStatus> statuses) {
        return statuses.stream().mapToLong(s -> counts.getOrDefault(s, 0L)).sum();
    }

    public record Dashboard(long totalShipments, long pendingBookings, long booked, long inTransit, long delivered,
                            long returned, long cancelled, List<PortalShipment> recentShipments) {
    }
}
