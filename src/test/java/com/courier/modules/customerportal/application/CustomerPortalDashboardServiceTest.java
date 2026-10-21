package com.courier.modules.customerportal.application;

import com.courier.modules.shipment.application.ShipmentPortalQueries;
import com.courier.modules.shipment.domain.ShipmentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerPortalDashboardServiceTest {

    private static final ActingCustomer ACTING = new ActingCustomer(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    @Mock private CurrentCustomerResolver resolver;
    @Mock private ShipmentPortalQueries shipments;
    @InjectMocks private CustomerPortalDashboardService service;

    @Test
    @DisplayName("groups the customer's own shipment statuses into dashboard stages")
    void groupsStatuses() {
        Map<ShipmentStatus, Long> counts = new EnumMap<>(ShipmentStatus.class);
        for (ShipmentStatus s : ShipmentStatus.values()) {
            counts.put(s, 0L);
        }
        counts.put(ShipmentStatus.BOOKED, 2L);
        counts.put(ShipmentStatus.READY_FOR_MANIFEST, 1L);
        counts.put(ShipmentStatus.DISPATCHED, 4L);
        counts.put(ShipmentStatus.OUT_FOR_DELIVERY, 1L);
        counts.put(ShipmentStatus.DELIVERED, 7L);
        counts.put(ShipmentStatus.CANCELLED, 1L);
        when(resolver.require()).thenReturn(ACTING);
        when(shipments.statusCounts(ACTING.companyId(), ACTING.customerId())).thenReturn(counts);
        when(shipments.recent(eq(ACTING.companyId()), eq(ACTING.customerId()), anyInt())).thenReturn(List.of());

        CustomerPortalDashboardService.Dashboard d = service.dashboard();

        assertThat(d.totalShipments()).isEqualTo(16);
        assertThat(d.booked()).isEqualTo(3);
        assertThat(d.inTransit()).isEqualTo(5);
        assertThat(d.delivered()).isEqualTo(7);
        assertThat(d.cancelled()).isEqualTo(1);
        assertThat(d.pendingBookings()).isZero();
    }
}
