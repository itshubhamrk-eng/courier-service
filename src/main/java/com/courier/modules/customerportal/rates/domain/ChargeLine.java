package com.courier.modules.customerportal.rates.domain;

import java.math.BigDecimal;

/** One line of a price, as a provider returned it (or as the markup/tax step added it). */
public record ChargeLine(String code, String label, ChargeComponent component, BigDecimal amount) {
}
