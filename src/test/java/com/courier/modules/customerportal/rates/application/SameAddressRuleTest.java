package com.courier.modules.customerportal.rates.application;

import com.courier.modules.customerportal.rates.application.RateComparisonService.CompareCommand;
import com.courier.modules.customerportal.rates.application.RateComparisonService.PartyInput;
import com.courier.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SameAddressRuleTest {

    private static CompareCommand cmd(PartyInput pickup, PartyInput delivery) {
        return new CompareCommand(pickup, delivery, null);
    }

    private static PartyInput party(String address, String pincode) {
        return new PartyInput("Asha", "9876543210", address, pincode, "Pune", "Maharashtra");
    }

    @Test
    @DisplayName("the same pincode and address, even with different case, spacing or punctuation, is refused")
    void sameAddressRefused() {
        assertThatThrownBy(() -> RateComparisonService.requireDifferentAddresses(
                cmd(party("Office 4, Thorat Chowk", "411016"), party("  office 4 thorat  chowk. ", "411016"))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("cannot be the same");
    }

    @Test
    @DisplayName("a different address or a different pincode is allowed")
    void differentAllowed() {
        assertThatCode(() -> RateComparisonService.requireDifferentAddresses(
                cmd(party("Office 4, Thorat Chowk", "411016"), party("Office 5, Thorat Chowk", "411016")))).doesNotThrowAnyException();
        assertThatCode(() -> RateComparisonService.requireDifferentAddresses(
                cmd(party("Office 4, Thorat Chowk", "411016"), party("Office 4, Thorat Chowk", "560001")))).doesNotThrowAnyException();
    }
}
