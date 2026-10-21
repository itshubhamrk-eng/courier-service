package com.courier.modules.customerportal.rates.infrastructure;

import com.courier.modules.customerportal.rates.domain.BookingOutcome;
import com.courier.modules.customerportal.rates.domain.BookingRequest;
import com.courier.modules.customerportal.rates.domain.ChargeComponent;
import com.courier.modules.customerportal.rates.domain.ChargeLine;
import com.courier.modules.customerportal.rates.domain.CourierProviderAdapter;
import com.courier.modules.customerportal.rates.domain.ProviderRate;
import com.courier.modules.customerportal.rates.domain.ProviderResult;
import com.courier.modules.customerportal.rates.domain.RateRequest;
import com.courier.modules.customerpricing.application.ZoneRateService;
import com.courier.modules.customerpricing.application.ZoneRateService.Quote;
import com.courier.modules.customerpricing.application.ZoneRateService.QuoteCommand;
import com.courier.modules.master.application.PincodeLocation;
import com.courier.modules.master.application.PincodeService;
import com.courier.modules.master.domain.Pincode;
import com.courier.modules.pickup.application.PickupService;
import com.courier.modules.pickup.domain.PickupRequest;
import com.courier.shared.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * OCCPL's own courier network, priced from the business customer's own rate card (pricing zones,
 * {@code ZoneRateService}) — the same rates booking at the counter uses. No markup. Runs on the request
 * thread: it needs the bound company and the signed-in principal.
 *
 * <p>The rate card charges actual weight (or the zone minimum), not volumetric weight.
 *
 * <p>Booking creates a <b>pickup request</b> in OCCPL's own operations flow (module pickup): operations
 * assign it, collect the parcel and book the shipment at the branch, which issues the tracking number. The
 * delivery details, contents and quoted total go into the pickup's remarks for the booking clerk.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OccplNetworkAdapter implements CourierProviderAdapter {

    public static final String CODE = "OCCPL_NETWORK";

    private final PincodeService pincodes;
    private final ZoneRateService zoneRates;
    private final PickupService pickups;

    @Override public String code() { return CODE; }
    @Override public String displayName() { return "OCCPL Network"; }
    @Override public boolean external() { return false; }
    @Override public boolean configured() { return true; }

    @Override
    public ProviderResult getRates(RateRequest r) {
        Optional<Pincode> pickup = find(r.pickupPincode());
        if (pickup.isEmpty() || !pickup.get().isServiceable() || !pickup.get().isPickupAvailable()) {
            return unavailable("OCCPL network does not pick up from " + r.pickupPincode() + ".");
        }
        PincodeLocation delivery = pincodes.location(r.deliveryPincode()).orElse(null);
        if (delivery == null || !delivery.serviceable() || delivery.districtId() == null) {
            return unavailable("OCCPL network does not deliver to " + r.deliveryPincode() + ".");
        }
        if (r.cod() && !delivery.codAvailable()) {
            return unavailable("Cash on delivery is not available at " + r.deliveryPincode() + " on OCCPL network.");
        }
        Quote q;
        try {
            q = zoneRates.calculate(new QuoteCommand(r.customerId(), delivery.districtId(), delivery.cityId(),
                    r.actualWeightKg(), r.declaredValue(), delivery.odaApplicable(), LocalDate.now()));
        } catch (ApiException e) {
            // No zone / no active rate / no slab for this weight: the customer has no OCCPL price here.
            log.debug("No OCCPL network rate for customer {} to {}: {}", r.customerId(), r.deliveryPincode(), e.getMessage());
            return unavailable("No OCCPL network rate is set up for this destination and weight. Contact your account manager.");
        }
        List<ChargeLine> lines = new ArrayList<>();
        lines.add(new ChargeLine("FREIGHT", "Freight", ChargeComponent.FREIGHT, q.freight()));
        add(lines, "DOCKET", "Docket charge", ChargeComponent.HANDLING, q.docketCharge());
        add(lines, "MATHADI", "Mathadi (loading) charge", ChargeComponent.HANDLING, q.mathadiCharge());
        add(lines, "PACKING", "Packing charge", ChargeComponent.HANDLING, q.packCharge());
        add(lines, "ODA", "Out-of-delivery-area charge", ChargeComponent.REMOTE_AREA, q.odaCharge());
        add(lines, "FUEL", "Fuel surcharge", ChargeComponent.FUEL_SURCHARGE, q.fuelSurcharge());
        add(lines, "FOV", "Risk cover (FOV)", ChargeComponent.INSURANCE, q.fovCharge());
        return ProviderResult.available(CODE, displayName(), List.of(new ProviderRate(
                "OCCPL_STANDARD", "OCCPL Standard", "SURFACE", null, null, q.chargeableWeight(), lines, null, null)));
    }

    @Override
    public BookingOutcome book(BookingRequest b) {
        PincodeLocation pickupAt = pincodes.location(b.pickup().pincode()).orElse(null);
        try {
            PickupRequest p = pickups.createFromCustomerPortal(new PickupService.SaveCommand(
                    b.customerId(), b.pickup().name(), b.pickup().phone(), b.pickup().address(), b.pickup().city(),
                    pickupAt == null ? null : pickupAt.districtId(), b.pickup().pincode(), b.rate().packageCount(),
                    b.rate().actualWeightKg(), null, null, remarks(b), null, null));
            return BookingOutcome.booked(p.getPickupNo(), null, null,
                    "Pickup " + p.getPickupNo() + " requested. The tracking number is issued when the parcel is collected and booked.");
        } catch (ApiException e) {
            // Local validation refused it inside our own transaction: nothing was created.
            return BookingOutcome.rejected("PICKUP_REFUSED", e.getMessage());
        }
    }

    static String remarks(BookingRequest b) {
        String text = "Portal booking " + b.reference() + ". Deliver to " + b.delivery().name() + ", " + b.delivery().phone()
                + ", " + b.delivery().address() + ", " + b.delivery().city() + " " + b.delivery().pincode()
                + ". Contents: " + b.contents() + ". Value " + b.rate().declaredValue().toPlainString()
                + ", " + (b.rate().cod() ? "COD" : "prepaid") + ", " + b.rate().packageCount() + " pkg, "
                + b.rate().lengthCm().toPlainString() + "x" + b.rate().breadthCm().toPlainString() + "x"
                + b.rate().heightCm().toPlainString() + " cm. Quoted total INR " + b.customerTotal().toPlainString() + ".";
        return text.length() <= 500 ? text : text.substring(0, 500);
    }

    private Optional<Pincode> find(String code) {
        try {
            return Optional.of(pincodes.findByCode(code));
        } catch (ApiException e) {
            return Optional.empty();
        }
    }

    private static void add(List<ChargeLine> lines, String code, String label, ChargeComponent c, BigDecimal amount) {
        if (amount != null && amount.signum() > 0) {
            lines.add(new ChargeLine(code, label, c, amount));
        }
    }

    private ProviderResult unavailable(String reason) {
        return ProviderResult.unavailable(CODE, displayName(), reason);
    }
}
