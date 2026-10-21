package com.courier.modules.customerportal.rates.domain;

import java.util.UUID;

/**
 * What a provider needs to find one booked shipment again: our booking's references as stored on
 * {@code portal_bookings}. Built from the booking row by the portal, never from browser input.
 */
public record ShipmentRef(UUID companyId, UUID customerId, String reference, String providerOrderRef,
                          String awbNumber, String labelUrl) {
}
