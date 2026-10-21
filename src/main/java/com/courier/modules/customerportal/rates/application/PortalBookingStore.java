package com.courier.modules.customerportal.rates.application;

import com.courier.modules.customerportal.application.ActingCustomer;
import com.courier.modules.customerportal.rates.domain.BookingOutcome;
import com.courier.modules.customerportal.rates.domain.PortalBooking;
import com.courier.modules.customerportal.rates.domain.PortalBookingRepository;
import com.courier.modules.customerportal.rates.domain.RateQuote;
import com.courier.modules.customerportal.rates.domain.RateQuoteOption;
import com.courier.modules.customerportal.rates.domain.RateQuoteOptionRepository;
import com.courier.modules.customerportal.rates.domain.RateQuoteRepository;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ErrorCode;
import com.courier.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * The two short transactions around a booking: {@link #start} (checks + SUBMITTING row, quote row-locked so
 * two bookings of one quote cannot both pass) and {@link #finish} (record the provider's outcome). The provider
 * call happens between them, outside any transaction.
 */
@Component
@RequiredArgsConstructor
public class PortalBookingStore {

    private final PortalBookingRepository bookings;
    private final RateQuoteRepository quotes;
    private final RateQuoteOptionRepository options;

    @Transactional
    public Start start(ActingCustomer acting, UUID quoteId, UUID optionId, String key, String hash, Instant now) {
        Optional<PortalBooking> existing = bookings.findByKey(acting.companyId(), acting.customerId(), key);
        if (existing.isPresent()) {
            return new Start(existing.get(), null, null, false);
        }
        RateQuote quote = quotes.findOwnedForUpdate(quoteId, acting.companyId(), acting.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Quote", quoteId));
        RateQuoteOption option = options.findByQuote(quoteId, acting.companyId()).stream()
                .filter(o -> o.getId().equals(optionId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Quote option", optionId));
        if (!option.isAvailable()) {
            throw new BusinessRuleException("That courier service cannot be booked.");
        }
        if (!quote.getExpiresAt().isAfter(now) || (option.getValidUntil() != null && !option.getValidUntil().isAfter(now))) {
            throw new BusinessRuleException(ErrorCode.QUOTE_EXPIRED, ErrorCode.QUOTE_EXPIRED.getDefaultMessage());
        }
        if (bookings.existsForQuote(quoteId, acting.companyId(), PortalBooking.Status.ACTIVE)) {
            throw new BusinessRuleException(ErrorCode.QUOTE_ALREADY_BOOKED, ErrorCode.QUOTE_ALREADY_BOOKED.getDefaultMessage());
        }
        PortalBooking booking = bookings.saveAndFlush(PortalBooking.builder()
                .customerId(acting.customerId())
                .quoteId(quoteId)
                .quoteOptionId(optionId)
                .idempotencyKey(key)
                .requestHash(hash)
                .providerCode(option.getProviderCode())
                .status(PortalBooking.Status.SUBMITTING)
                .totalAmount(option.getTotalAmount())
                .currency(option.getCurrency())
                .submittedAt(now)
                .build());
        return new Start(booking, quote, option, true);
    }

    @Transactional
    public PortalBooking finish(ActingCustomer acting, UUID bookingId, BookingOutcome outcome, Instant now) {
        PortalBooking b = bookings.findOwned(bookingId, acting.companyId(), acting.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
        b.setProviderOrderRef(trim(outcome.providerOrderRef(), 120));
        b.setAwbNumber(trim(outcome.awbNumber(), 60));
        b.setLabelUrl(trim(outcome.labelUrl(), 1024));
        switch (outcome.result()) {
            case BOOKED -> {
                b.setStatus(PortalBooking.Status.BOOKED);
                b.setBookedAt(now);
                quotes.findOwned(b.getQuoteId(), acting.companyId(), acting.customerId()).ifPresent(q -> q.setStatus("BOOKED"));
            }
            case REJECTED -> {
                b.setStatus(PortalBooking.Status.FAILED);
                b.setFailureCode(trim(outcome.failureCode(), 60));
                b.setFailureMessage(trim(outcome.message(), 500));
            }
            case UNKNOWN -> {
                b.setStatus(PortalBooking.Status.OUTCOME_UNKNOWN);
                b.setFailureCode(trim(outcome.failureCode(), 60));
                b.setFailureMessage(trim(outcome.message(), 500));
            }
        }
        return bookings.save(b);
    }

    @Transactional(readOnly = true)
    public Optional<PortalBooking> findByKey(ActingCustomer acting, String key) {
        return bookings.findByKey(acting.companyId(), acting.customerId(), key);
    }

    @Transactional(readOnly = true)
    public Optional<PortalBooking> findOwned(ActingCustomer acting, UUID id) {
        return bookings.findOwned(id, acting.companyId(), acting.customerId());
    }

    private static String trim(String s, int max) {
        return s == null ? null : (s.length() <= max ? s : s.substring(0, max));
    }

    /** {@code created=false}: the key was already used — {@code booking} is that earlier booking. */
    public record Start(PortalBooking booking, RateQuote quote, RateQuoteOption option, boolean created) {
    }
}
