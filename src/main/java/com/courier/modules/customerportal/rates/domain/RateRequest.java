package com.courier.modules.customerportal.rates.domain;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * What every provider is asked to price. Dimensions are per package, already in centimetres;
 * weight is the total actual weight in kilograms.
 */
public record RateRequest(UUID companyId,
                          UUID customerId,
                          String pickupPincode,
                          String deliveryPincode,
                          BigDecimal actualWeightKg,
                          BigDecimal lengthCm,
                          BigDecimal breadthCm,
                          BigDecimal heightCm,
                          int packageCount,
                          BigDecimal declaredValue,
                          PaymentMode paymentMode) {

    public enum PaymentMode { PREPAID, COD }

    public boolean cod() {
        return paymentMode == PaymentMode.COD;
    }
}
