package com.courier.modules.customerportal.api;

import com.courier.modules.customerportal.rates.application.PortalBookingService;
import com.courier.modules.customerportal.rates.application.PortalBookingService.BookingView;
import com.courier.modules.customerportal.rates.application.PortalShipmentService;
import com.courier.modules.customerportal.rates.application.PortalShipmentService.BookingDetail;
import com.courier.modules.customerportal.rates.application.PortalShipmentService.BookingSummary;
import com.courier.modules.customerportal.rates.application.PortalShipmentService.Label;
import com.courier.modules.customerportal.rates.application.PortalShipmentService.TrackingView;
import com.courier.shared.api.ApiResponse;
import com.courier.shared.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Booking a selected quote option (one booking per Idempotency-Key, whatever the number of retries), then the
 * customer's bookings: history, detail, tracking and label.
 */
@RestController
@RequestMapping("/api/v1/customer-portal")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Customer portal")
public class CustomerPortalBookingController {

    private final PortalBookingService service;
    private final PortalShipmentService shipments;

    @PostMapping("/shipments")
    @Operation(summary = "Book a courier service from a quote",
            description = "Send a new `Idempotency-Key` per Confirm click and reuse it for retries: a repeat returns the "
                    + "same booking. 409 QUOTE_EXPIRED (refresh rates), QUOTE_ALREADY_BOOKED, IDEMPOTENCY_KEY_REUSED. "
                    + "Status BOOKED, FAILED (nothing booked) or OUTCOME_UNKNOWN (our team confirms with the courier).")
    public ResponseEntity<ApiResponse<BookingView>> book(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                                         @Valid @RequestBody BookRequest request) {
        BookingView view = service.book(request.quoteId(), request.optionId(), idempotencyKey);
        HttpStatus status = "BOOKED".equals(view.status()) ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.success(view));
    }

    @GetMapping("/bookings")
    @Operation(summary = "The customer's bookings, newest first",
            description = "`status`: PENDING, SUBMITTING, BOOKED, FAILED or OUTCOME_UNKNOWN; omit for all. `size` at most 50.")
    public ApiResponse<PageResponse<BookingSummary>> bookings(@RequestParam(required = false) String status,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(PageResponse.from(shipments.list(status, page, size)));
    }

    @GetMapping("/bookings/{bookingId}")
    @Operation(summary = "One of the customer's own bookings in full (404 for anyone else's)")
    public ApiResponse<BookingDetail> booking(@PathVariable UUID bookingId) {
        return ApiResponse.success(shipments.detail(bookingId));
    }

    @GetMapping("/bookings/{bookingId}/tracking")
    @Operation(summary = "Live tracking from the courier",
            description = "Asked of the courier on every call (30 tracking/label calls a minute per account). "
                    + "`available=false` with a message when the courier has nothing yet or cannot be reached.")
    public ApiResponse<TrackingView> tracking(@PathVariable UUID bookingId) {
        return ApiResponse.success(shipments.tracking(bookingId));
    }

    @GetMapping(value = "/bookings/{bookingId}/label", produces = {MediaType.APPLICATION_PDF_VALUE, MediaType.APPLICATION_JSON_VALUE})
    @Operation(summary = "Download the shipping label (PDF)",
            description = "Fetched from the courier by the server. 422 with a message when the courier has no label yet.")
    public ResponseEntity<byte[]> label(@PathVariable UUID bookingId) {
        Label label = shipments.label(bookingId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(label.fileName()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(label.pdf());
    }

    public record BookRequest(@NotNull UUID quoteId, @NotNull UUID optionId) {
    }
}
