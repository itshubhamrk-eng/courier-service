package com.courier.modules.customerportal.rates.domain;

/**
 * Kind of a charge line. The markup applies only to the components a markup rule names
 * ({@code pricing_markup_rules.applies_to}, FREIGHT by default); TAX is never marked up.
 */
public enum ChargeComponent {
    FREIGHT,
    FUEL_SURCHARGE,
    COD,
    REMOTE_AREA,
    HANDLING,
    INSURANCE,
    OTHER,
    TAX
}
