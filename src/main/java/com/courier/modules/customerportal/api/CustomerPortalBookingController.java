package com.courier.modules.customerportal.api;

import com.courier.modules.customerportal.rates.application.PortalBookingService;
import com.courier.modules.customerportal.rates.application.PortalBookingService.BookingView;
import com.courier.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Booking a selected quote option. One booking per Idempotency-Key, whatever the number of retries. */
@RestController
@RequestMapping("/api/v1/customer-portal")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Customer portal")
public class CustomerPortalBookingController {

    private final PortalBookingService service;

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

    @GetMapping("/bookings/{bookingId}")
    @Operation(summary = "One of the customer's own bookings (404 for anyone else's)")
    public ApiResponse<BookingView> booking(@PathVariable UUID bookingId) {
        return ApiResponse.success(service.get(bookingId));
    }

    public record BookRequest(@NotNull UUID quoteId, @NotNull UUID optionId) {
    }
}
