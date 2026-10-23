package com.courier.modules.customerportal.rates.application;

import com.courier.modules.customerportal.rates.domain.CourierProviderAdapter;
import com.courier.modules.customerportal.rates.domain.DeliveryStage;
import com.courier.modules.customerportal.rates.domain.PortalBooking;
import com.courier.modules.customerportal.rates.domain.PortalBookingRepository;
import com.courier.modules.customerportal.rates.domain.ShipmentRef;
import com.courier.modules.customerportal.rates.domain.TrackingEvent;
import com.courier.modules.customerportal.rates.domain.TrackingResult;
import com.courier.shared.company.CompanyContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrackingSyncServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-23T10:00:00Z");
    private static final UUID COMPANY = UUID.randomUUID();

    private final PortalBookingRepository bookings = mock(PortalBookingRepository.class);
    private final CourierProviderAdapter delhivery = mock(CourierProviderAdapter.class);
    private final CustomerPortalProperties properties = new CustomerPortalProperties();
    private final AtomicReference<UUID> companySeen = new AtomicReference<>();
    private TrackingSyncService service;

    @BeforeEach
    void setUp() {
        when(delhivery.code()).thenReturn("DELHIVERY");
        service = new TrackingSyncService(bookings, List.of(delhivery), properties,
                new TransactionTemplate(mock(PlatformTransactionManager.class)));
    }

    private PortalBooking booked(Instant bookedAt) {
        PortalBooking b = PortalBooking.builder().customerId(UUID.randomUUID()).providerCode("DELHIVERY")
                .status(PortalBooking.Status.BOOKED).awbNumber("AWB1").bookedAt(bookedAt).build();
        b.setId(UUID.randomUUID());
        b.setCompanyId(COMPANY);
        return b;
    }

    private void courierSays(TrackingResult result) {
        when(delhivery.track(any(ShipmentRef.class))).thenAnswer(i -> {
            companySeen.set(CompanyContext.getCompanyId().orElse(null));
            return result;
        });
    }

    @Test
    @DisplayName("asks the courier inside the booking's company and saves the stage; asks again in an hour")
    void syncsInTransit() {
        PortalBooking b = booked(NOW.minus(Duration.ofDays(1)));
        when(bookings.findDueForTrackingSyncAcrossCompanies(eq(NOW), any())).thenReturn(List.of(b));
        courierSays(TrackingResult.of("In Transit", "AWB1", "Delhi Hub",
                List.of(new TrackingEvent(NOW.minusSeconds(600), "In Transit", "Delhi Hub", "Reached hub"))));

        assertThat(service.syncDue(NOW)).isEqualTo(1);

        assertThat(companySeen.get()).isEqualTo(COMPANY);
        assertThat(CompanyContext.isSet()).isFalse();
        verify(bookings).recordDelivery(b.getId(), COMPANY, DeliveryStage.IN_TRANSIT, "In Transit", "Delhi Hub",
                NOW.minusSeconds(600), NOW, NOW.plus(Duration.ofHours(1)));
    }

    @Test
    @DisplayName("a delivered shipment is saved and never asked about again")
    void stopsWhenDelivered() {
        PortalBooking b = booked(NOW.minus(Duration.ofDays(2)));
        when(bookings.findDueForTrackingSyncAcrossCompanies(eq(NOW), any())).thenReturn(List.of(b));
        courierSays(TrackingResult.of("Delivered", "AWB1", "Delhi", List.of()));

        service.syncDue(NOW);

        verify(bookings).recordDelivery(eq(b.getId()), eq(COMPANY), eq(DeliveryStage.DELIVERED), eq("Delivered"), eq("Delhi"),
                isNull(), eq(NOW), isNull());
    }

    @Test
    @DisplayName("no answer, an unreadable one or an error only reschedules; a too-old booking stops")
    void reschedulesOrStops() {
        PortalBooking unavailable = booked(NOW.minus(Duration.ofDays(1)));
        PortalBooking old = booked(NOW.minus(Duration.ofDays(60)));
        when(bookings.findDueForTrackingSyncAcrossCompanies(eq(NOW), any())).thenReturn(List.of(unavailable, old));
        courierSays(TrackingResult.unavailable("Down"));

        service.syncDue(NOW);

        verify(bookings).recordSyncAttempt(unavailable.getId(), COMPANY, NOW, NOW.plus(Duration.ofHours(1)));
        verify(bookings).recordSyncAttempt(old.getId(), COMPANY, NOW, null);
        verify(bookings, never()).recordDelivery(any(), any(), any(), any(), any(), any(), any(), any());

        PortalBooking failing = booked(NOW.minus(Duration.ofDays(1)));
        when(bookings.findDueForTrackingSyncAcrossCompanies(eq(NOW), any())).thenReturn(List.of(failing));
        when(delhivery.track(any(ShipmentRef.class))).thenThrow(new IllegalStateException("boom"));
        service.syncDue(NOW);
        verify(bookings).recordSyncAttempt(failing.getId(), COMPANY, NOW, NOW.plus(Duration.ofHours(1)));
    }

    @Test
    @DisplayName("switched off: no courier is asked")
    void disabled() {
        properties.getTrackingSync().setEnabled(false);

        assertThat(service.syncDue(NOW)).isZero();

        verify(bookings, never()).findDueForTrackingSyncAcrossCompanies(any(), any());
    }

    @Test
    @DisplayName("a pushed status is saved at once and confirmed with the courier shortly after; unreadable ones only ask")
    void pushed() {
        PortalBooking b = booked(NOW.minus(Duration.ofDays(1)));
        PortalBooking failed = booked(NOW);
        failed.setStatus(PortalBooking.Status.FAILED);
        when(bookings.findByAwb(COMPANY, "DELHIVERY", "AWB1")).thenReturn(List.of(b, failed));

        assertThat(service.recordPushed(COMPANY, "DELHIVERY", " AWB1 ", "Out for delivery", "Delhi", null, NOW)).isEqualTo(2);
        verify(bookings).recordDelivery(b.getId(), COMPANY, DeliveryStage.OUT_FOR_DELIVERY, "Out for delivery", "Delhi", null,
                NOW, NOW.plusSeconds(30));
        verify(bookings, never()).recordDelivery(eq(failed.getId()), any(), any(), any(), any(), any(), any(), any());

        service.recordPushed(COMPANY, "DELHIVERY", "AWB1", "Weather delay", null, null, NOW);
        verify(bookings).syncSoon(b.getId(), COMPANY, NOW.plusSeconds(30));

        assertThat(service.recordPushed(COMPANY, "DELHIVERY", " ", "Delivered", null, null, NOW)).isZero();
    }
}
