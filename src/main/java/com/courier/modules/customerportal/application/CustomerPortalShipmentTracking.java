package com.courier.modules.customerportal.application;

import com.courier.modules.shipment.application.ShipmentPortalQueries;
import com.courier.modules.shipment.application.ShipmentPortalQueries.PortalShipment;
import com.courier.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Status history of a shipment booked for the customer at an OCCPL counter (the dashboard's recent
 * shipments). The customer's own shipments only; anyone else's answers 404.
 */
@Service
@RequiredArgsConstructor
public class CustomerPortalShipmentTracking {

    private final CurrentCustomerResolver resolver;
    private final ShipmentPortalQueries shipments;

    public ShipmentTracking track(UUID shipmentId) {
        ActingCustomer acting = resolver.require();
        ShipmentPortalQueries.PortalTimeline t = shipments.timeline(acting.companyId(), acting.customerId(), shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment", shipmentId));
        List<Step> steps = t.steps().stream()
                .map(s -> new Step(s.status().name(), s.at(), s.branchName()))
                .toList()
                .reversed();
        return new ShipmentTracking(t.shipment(), steps);
    }

    /** Steps newest first. */
    public record ShipmentTracking(PortalShipment shipment, List<Step> steps) {
    }

    public record Step(String status, Instant at, String location) {
    }
}
