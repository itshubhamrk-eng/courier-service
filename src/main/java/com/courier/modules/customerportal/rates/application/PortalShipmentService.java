package com.courier.modules.customerportal.rates.application;

import com.courier.modules.customerportal.application.ActingCustomer;
import com.courier.modules.customerportal.application.CurrentCustomerResolver;
import com.courier.modules.customerportal.rates.application.PortalBookingStore.Row;
import com.courier.modules.customerportal.rates.application.RateComparisonService.BreakdownLine;
import com.courier.modules.customerportal.rates.domain.BookingRequest;
import com.courier.modules.customerportal.rates.domain.CourierProviderAdapter;
import com.courier.modules.customerportal.rates.domain.LabelResult;
import com.courier.modules.customerportal.rates.domain.PortalBooking;
import com.courier.modules.customerportal.rates.domain.RateQuote;
import com.courier.modules.customerportal.rates.domain.RateQuoteOption;
import com.courier.modules.customerportal.rates.domain.ShipmentRef;
import com.courier.modules.customerportal.rates.domain.TrackingEvent;
import com.courier.modules.customerportal.rates.domain.TrackingResult;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * After booking: the customer's booking history, one booking in full, live tracking and the shipping label.
 * Every read is of the signed-in customer's own bookings (404 for anyone else's). Tracking is asked of the
 * provider on each view and not stored; labels are downloaded by the server from the provider and streamed,
 * so the provider's URL never reaches the browser.
 */
@Slf4j
@Service
public class PortalShipmentService {

    static final int MAX_PAGE_SIZE = 50;

    private final CurrentCustomerResolver resolver;
    private final PortalBookingStore store;
    private final List<CourierProviderAdapter> adapters;
    private final ProviderCallLimiter limiter;
    private final LabelFetcher labels;

    public PortalShipmentService(CurrentCustomerResolver resolver, PortalBookingStore store,
                                 List<CourierProviderAdapter> adapters, ProviderCallLimiter limiter, LabelFetcher labels) {
        this.resolver = resolver;
        this.store = store;
        this.adapters = adapters;
        this.limiter = limiter;
        this.labels = labels;
    }

    /** Newest first. {@code status} is one booking status, or null/blank for all. */
    public Page<BookingSummary> list(String status, int page, int size) {
        ActingCustomer acting = resolver.require();
        Set<PortalBooking.Status> statuses = statuses(status);
        PageRequest request = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, MAX_PAGE_SIZE)));
        return store.page(acting, statuses, request).map(this::summary);
    }

    public BookingDetail detail(UUID bookingId) {
        ActingCustomer acting = resolver.require();
        return detail(store.row(acting, bookingId).orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId)));
    }

    public TrackingView tracking(UUID bookingId) {
        ActingCustomer acting = resolver.require();
        PortalBooking b = store.findOwned(acting, bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
        CourierProviderAdapter adapter = adapter(b.getProviderCode());
        if (b.getStatus() != PortalBooking.Status.BOOKED) {
            return new TrackingView(b.getId(), adapter.displayName(), b.getAwbNumber(), false, null, null, List.of(),
                    b.getStatus() == PortalBooking.Status.FAILED ? "This booking was not placed, so there is nothing to track."
                            : "Tracking starts once the booking is confirmed.", Instant.now());
        }
        limiter.check(acting.userId());
        TrackingResult result;
        try {
            result = adapter.track(ref(acting, b));
        } catch (RuntimeException e) {
            log.error("Tracking booking {} with {} failed unexpectedly", b.getId(), adapter.code(), e);
            result = TrackingResult.unavailable("Tracking is not available right now. Please try again later.");
        }
        if (result.trackingNumber() != null && b.getAwbNumber() == null) {
            store.recordTrackingNumber(acting, b.getId(), result.trackingNumber());
        }
        return new TrackingView(b.getId(), adapter.displayName(),
                result.trackingNumber() != null ? result.trackingNumber() : b.getAwbNumber(), result.available(),
                result.status(), result.currentLocation(), result.events(), result.message(), Instant.now());
    }

    /**
     * The label PDF. A stored provider URL is tried first; if it no longer answers (expired link) it is
     * forgotten and the provider asked once for a fresh one.
     */
    public Label label(UUID bookingId) {
        ActingCustomer acting = resolver.require();
        PortalBooking b = store.findOwned(acting, bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
        if (b.getStatus() != PortalBooking.Status.BOOKED) {
            throw new BusinessRuleException("A label is available only for a confirmed booking.");
        }
        CourierProviderAdapter adapter = adapter(b.getProviderCode());
        limiter.check(acting.userId());
        String fileName = "label-" + (b.getAwbNumber() != null ? b.getAwbNumber().replaceAll("[^A-Za-z0-9_-]", "")
                : b.getId().toString()) + ".pdf";
        if (b.getLabelUrl() != null) {
            Optional<byte[]> pdf = labels.fetch(b.getLabelUrl(), adapter.labelHosts());
            if (pdf.isPresent()) {
                return new Label(fileName, pdf.get());
            }
            store.recordLabelUrl(acting, b.getId(), null);
        }
        LabelResult fresh;
        try {
            fresh = adapter.label(ref(acting, b));
        } catch (RuntimeException e) {
            log.error("Label for booking {} with {} failed unexpectedly", b.getId(), adapter.code(), e);
            fresh = LabelResult.notAvailable("The label is not available right now. Please try again later.");
        }
        if (!fresh.available()) {
            throw new BusinessRuleException(fresh.message());
        }
        byte[] pdf = labels.fetch(fresh.url(), adapter.labelHosts())
                .orElseThrow(() -> new BusinessRuleException("The courier's label could not be downloaded. Please try again later."));
        store.recordLabelUrl(acting, b.getId(), fresh.url());
        return new Label(fileName, pdf);
    }

    // ------------------------------------------------------------------ mapping

    private BookingSummary summary(Row row) {
        PortalBooking b = row.booking();
        Map<String, Object> snapshot = row.quote() == null ? Map.of() : row.quote().getRequest();
        BookingRequest.Party pickup = BookingRequest.Party.of(snapshot.get("pickup"));
        BookingRequest.Party delivery = BookingRequest.Party.of(snapshot.get("delivery"));
        RateQuoteOption o = row.option();
        return new BookingSummary(b.getId(), b.getStatus().name(), b.getProviderCode(), providerName(b.getProviderCode()),
                o == null ? null : o.getServiceName(), o == null ? null : o.getTransportMode(), b.getProviderOrderRef(),
                b.getAwbNumber(), pickup.city(), pickup.pincode(), delivery.name(), delivery.city(), delivery.pincode(),
                row.quote() == null ? null : row.quote().getPackageCount(), b.getTotalAmount(), b.getCurrency(),
                b.getCreatedAt(), b.getBookedAt());
    }

    private BookingDetail detail(Row row) {
        PortalBooking b = row.booking();
        RateQuote q = row.quote();
        RateQuoteOption o = row.option();
        Map<String, Object> snapshot = q == null ? Map.of() : q.getRequest();
        @SuppressWarnings("unchecked")
        Map<String, Object> pkg = snapshot.get("package") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        Parcel parcel = q == null ? null : new Parcel(q.getActualWeightKg(), q.getVolumetricWeightKg(),
                o == null ? null : o.getChargeableWeightKg(), q.getPackageCount(), num(pkg.get("length")),
                num(pkg.get("breadth")), num(pkg.get("height")), str(pkg.get("dimensionUnit")), str(pkg.get("contents")),
                q.getDeclaredValue(), q.getPaymentMode());
        return new BookingDetail(b.getId(), b.getQuoteId(), b.getStatus().name(), b.getProviderCode(), providerName(b.getProviderCode()),
                o == null ? null : o.getServiceName(), o == null ? null : o.getTransportMode(),
                o == null ? null : o.getEtaMinDays(), o == null ? null : o.getEtaMaxDays(), b.getProviderOrderRef(),
                b.getAwbNumber(), b.getStatus() == PortalBooking.Status.BOOKED && labelsOffered(b.getProviderCode()),
                b.getFailureMessage(),
                BookingRequest.Party.of(snapshot.get("pickup")), BookingRequest.Party.of(snapshot.get("delivery")), parcel,
                o == null ? List.of() : RateComparisonService.lines(o), b.getTotalAmount(), b.getCurrency(),
                b.getSubmittedAt(), b.getBookedAt(), b.getCreatedAt());
    }

    private static Set<PortalBooking.Status> statuses(String raw) {
        if (raw == null || raw.isBlank()) {
            return EnumSet.allOf(PortalBooking.Status.class);
        }
        try {
            return EnumSet.of(PortalBooking.Status.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("Unknown booking status: " + raw);
        }
    }

    private static ShipmentRef ref(ActingCustomer acting, PortalBooking b) {
        return new ShipmentRef(acting.companyId(), acting.customerId(), b.getId().toString(), b.getProviderOrderRef(),
                b.getAwbNumber(), b.getLabelUrl());
    }

    private CourierProviderAdapter adapter(String code) {
        return adapters.stream().filter(a -> a.code().equals(code)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown provider " + code));
    }

    /** Providers without label hosts (OCCPL's own network prints labels at the branch) never offer a download. */
    private boolean labelsOffered(String code) {
        return adapters.stream().filter(a -> a.code().equals(code)).anyMatch(a -> !a.labelHosts().isEmpty());
    }

    private String providerName(String code) {
        return adapters.stream().filter(a -> a.code().equals(code)).map(CourierProviderAdapter::displayName)
                .findFirst().orElse(code);
    }

    private static BigDecimal num(Object o) {
        try {
            return o == null ? null : new BigDecimal(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    // ------------------------------------------------------------------ shapes

    /** One row of the history list. {@code bookingReference} is the provider's order or pickup number. */
    public record BookingSummary(UUID bookingId, String status, String providerCode, String providerName,
                                 String serviceName, String transportMode, String bookingReference, String trackingNumber,
                                 String pickupCity, String pickupPincode, String deliveryName, String deliveryCity,
                                 String deliveryPincode, Integer packageCount, BigDecimal totalAmount, String currency,
                                 Instant createdAt, Instant bookedAt) {
    }

    /** One booking as it was quoted and booked; the amounts are the frozen quote's. */
    public record BookingDetail(UUID bookingId, UUID quoteId, String status, String providerCode, String providerName,
                                String serviceName, String transportMode, Integer etaMinDays, Integer etaMaxDays,
                                String bookingReference, String trackingNumber, boolean labelDownloadable, String message,
                                BookingRequest.Party pickup, BookingRequest.Party delivery, Parcel parcel,
                                List<BreakdownLine> charges, BigDecimal totalAmount, String currency,
                                Instant submittedAt, Instant bookedAt, Instant createdAt) {
    }

    public record Parcel(BigDecimal actualWeightKg, BigDecimal volumetricWeightKg, BigDecimal chargeableWeightKg,
                         Integer packageCount, BigDecimal length, BigDecimal breadth, BigDecimal height,
                         String dimensionUnit, String contents, BigDecimal declaredValue, String paymentMode) {
    }

    public record TrackingView(UUID bookingId, String providerName, String trackingNumber, boolean available,
                               String status, String currentLocation, List<TrackingEvent> events, String message,
                               Instant retrievedAt) {
    }

    public record Label(String fileName, byte[] pdf) {
    }
}
