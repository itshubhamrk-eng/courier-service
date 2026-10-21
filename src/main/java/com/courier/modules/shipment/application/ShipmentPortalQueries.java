package com.courier.modules.shipment.application;

import com.courier.modules.shipment.domain.Shipment;
import com.courier.modules.shipment.domain.ShipmentRepository;
import com.courier.modules.shipment.domain.ShipmentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only views of one business customer's shipments ({@code shipments.business_customer_id})
 * for the customer portal. No {@code @PreAuthorize}: the portal resolves the customer from the
 * signed-in account and passes it in; nothing else may call this with a customer id from a request.
 */
@Service
@RequiredArgsConstructor
public class ShipmentPortalQueries {

    private static final int MAX_RECENT = 50;

    private final ShipmentRepository repository;

    @Transactional(readOnly = true)
    public Map<ShipmentStatus, Long> statusCounts(UUID companyId, UUID customerId) {
        Map<ShipmentStatus, Long> counts = new EnumMap<>(ShipmentStatus.class);
        for (ShipmentStatus s : ShipmentStatus.values()) {
            counts.put(s, 0L);
        }
        repository.countByStatusForBusinessCustomer(companyId, customerId)
                .forEach(row -> counts.put(row.getStatus(), row.getTotal()));
        return counts;
    }

    @Transactional(readOnly = true)
    public List<PortalShipment> recent(UUID companyId, UUID customerId, int limit) {
        int size = Math.max(1, Math.min(limit, MAX_RECENT));
        return repository.findRecentForBusinessCustomer(companyId, customerId, PageRequest.of(0, size)).stream()
                .map(ShipmentPortalQueries::view)
                .toList();
    }

    private static PortalShipment view(Shipment s) {
        return new PortalShipment(s.getId(), s.getShipmentNumber(), s.getTrackingNumber(), s.getBookingDate(),
                s.getStatus(), s.getReceiverName(), s.getNumberOfPackages(), s.getChargeableWeight(),
                s.getExpectedDeliveryDate(), s.getCreatedAt());
    }

    /** What the portal shows of a shipment: no internal ids, charges or staff data. */
    public record PortalShipment(UUID id, String shipmentNumber, String trackingNumber, LocalDate bookingDate,
                                 ShipmentStatus status, String receiverName, Integer packages,
                                 BigDecimal chargeableWeight, LocalDate expectedDeliveryDate, Instant createdAt) {
    }
}
