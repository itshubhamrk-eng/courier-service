package com.courier.modules.customerportal.rates.infrastructure;

import com.courier.modules.customerportal.rates.domain.BookingOutcome;
import com.courier.modules.customerportal.rates.domain.ChargeComponent;
import com.courier.modules.customerportal.rates.domain.ProviderResult;
import com.courier.modules.customerportal.rates.domain.RateRequest;
import com.courier.modules.customerpricing.application.ZoneRateService;
import com.courier.modules.master.application.PincodeLocation;
import com.courier.modules.master.application.PincodeService;
import com.courier.modules.master.domain.Pincode;
import com.courier.modules.pickup.application.PickupService;
import com.courier.modules.pickup.domain.PickupRequest;
import com.courier.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OccplNetworkAdapterTest {

    @Mock private PincodeService pincodes;
    @Mock private ZoneRateService zoneRates;
    @Mock private PickupService pickups;
    @InjectMocks private OccplNetworkAdapter adapter;

    private static final UUID DISTRICT = UUID.randomUUID();

    private RateRequest request() {
        return new RateRequest(UUID.randomUUID(), UUID.randomUUID(), "411016", "110001", new BigDecimal("12"),
                new BigDecimal("30"), new BigDecimal("20"), new BigDecimal("10"), 1, new BigDecimal("5000"),
                RateRequest.PaymentMode.PREPAID);
    }

    private void served() {
        Pincode pickup = mock(Pincode.class);
        when(pickup.isServiceable()).thenReturn(true);
        when(pickup.isPickupAvailable()).thenReturn(true);
        when(pincodes.findByCode("411016")).thenReturn(pickup);
        when(pincodes.location("110001")).thenReturn(Optional.of(
                new PincodeLocation(UUID.randomUUID(), UUID.randomUUID(), null, DISTRICT, true, true, false)));
    }

    @Test
    @DisplayName("prices from the customer's own rate card, one line per charge, no zero lines")
    void pricesFromRateCard() {
        served();
        when(zoneRates.calculate(any())).thenReturn(new ZoneRateService.Quote(UUID.randomUUID(), "North",
                new BigDecimal("12"), new BigDecimal("12"), new BigDecimal("40"), new BigDecimal("480.00"),
                new BigDecimal("50"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("48.00"),
                new BigDecimal("10.00"), new BigDecimal("588.00")));

        ProviderResult result = adapter.getRates(request());

        assertThat(result.available()).isTrue();
        assertThat(result.rates().get(0).providerTotal()).isEqualByComparingTo("588.00");
        assertThat(result.rates().get(0).charges()).extracting(l -> l.component()).containsExactly(
                ChargeComponent.FREIGHT, ChargeComponent.HANDLING, ChargeComponent.FUEL_SURCHARGE, ChargeComponent.INSURANCE);
    }

    @Test
    @DisplayName("no zone/rate for the destination means unavailable, not an error")
    void noRateCard() {
        served();
        when(zoneRates.calculate(any())).thenThrow(new BusinessRuleException("No active pricing zone"));

        ProviderResult result = adapter.getRates(request());

        assertThat(result.available()).isFalse();
        assertThat(result.reason()).contains("No OCCPL network rate");
    }

    @Test
    @DisplayName("a delivery pincode outside the network is refused before pricing")
    void notServed() {
        served();
        when(pincodes.location("110001")).thenReturn(Optional.empty());

        assertThat(adapter.getRates(request()).available()).isFalse();
    }

    @Test
    @DisplayName("booking creates a pickup request for the customer, with delivery details for the booking clerk")
    void booksAsPickup() {
        served();
        when(pickups.createFromCustomerPortal(any())).thenAnswer(i -> {
            PickupService.SaveCommand c = i.getArgument(0);
            assertThat(c.remarks()).contains("Deliver to Ravi").contains("Quoted total INR 111.80");
            assertThat(c.remarks().length()).isLessThanOrEqualTo(500);
            return PickupRequest.builder().pickupNo("PU-0001").build();
        });

        BookingOutcome o = adapter.book(ShiprocketAdapterTest.booking());

        assertThat(o.result()).isEqualTo(BookingOutcome.Result.BOOKED);
        assertThat(o.providerOrderRef()).isEqualTo("PU-0001");
        assertThat(o.awbNumber()).isNull();
    }

    @Test
    @DisplayName("a refused pickup is a definite failure (nothing was created)")
    void pickupRefused() {
        served();
        when(pickups.createFromCustomerPortal(any())).thenThrow(new BusinessRuleException("A valid contact mobile is required."));

        assertThat(adapter.book(ShiprocketAdapterTest.booking()).result()).isEqualTo(BookingOutcome.Result.REJECTED);
    }
}
