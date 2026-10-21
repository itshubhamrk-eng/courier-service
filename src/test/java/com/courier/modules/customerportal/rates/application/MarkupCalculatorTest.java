package com.courier.modules.customerportal.rates.application;

import com.courier.modules.customerportal.rates.application.MarkupCalculator.Markup;
import com.courier.modules.customerportal.rates.application.MarkupCalculator.Priced;
import com.courier.modules.customerportal.rates.domain.ChargeComponent;
import com.courier.modules.customerportal.rates.domain.ChargeLine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarkupCalculatorTest {

    private static ChargeLine line(ChargeComponent c, String amount) {
        return new ChargeLine(c.name(), c.name(), c, new BigDecimal(amount));
    }

    private static Markup markup(String pct, ChargeComponent... on) {
        return new Markup(null, true, new BigDecimal(pct), EnumSet.of(on[0], on), new BigDecimal("18"));
    }

    @Test
    @DisplayName("the illustrative case: ₹100 freight + 10 % = ₹110, plus GST on the ₹10 service charge")
    void hundredRupees() {
        Priced p = MarkupCalculator.price(List.of(line(ChargeComponent.FREIGHT, "100")),
                markup("10", ChargeComponent.FREIGHT), null);

        assertThat(p.markupBase()).isEqualByComparingTo("100.00");
        assertThat(p.markupAmount()).isEqualByComparingTo("10.00");
        assertThat(p.taxAmount()).isEqualByComparingTo("1.80");
        assertThat(p.total()).isEqualByComparingTo("111.80");
    }

    @Test
    @DisplayName("markup only on the configured components; provider tax is neither marked up nor taxed again")
    void onlyConfiguredComponents() {
        Priced p = MarkupCalculator.price(List.of(
                        line(ChargeComponent.FREIGHT, "200"), line(ChargeComponent.FUEL_SURCHARGE, "30"),
                        line(ChargeComponent.COD, "40"), line(ChargeComponent.TAX, "48.60")),
                markup("10", ChargeComponent.FREIGHT, ChargeComponent.FUEL_SURCHARGE), new BigDecimal("18"));

        assertThat(p.markupBase()).isEqualByComparingTo("230.00");
        assertThat(p.markupAmount()).isEqualByComparingTo("23.00");
        // provider tax 48.60 kept as is (no extra GST because the provider already taxed) + 4.14 GST on the markup
        assertThat(p.taxAmount()).isEqualByComparingTo("52.74");
        assertThat(p.total()).isEqualByComparingTo("345.74");
        assertThat(p.otherCharges()).isEqualByComparingTo("70.00");
    }

    @Test
    @DisplayName("rounds half up to paise at each line")
    void roundsHalfUp() {
        Priced p = MarkupCalculator.price(List.of(line(ChargeComponent.FREIGHT, "333.35")),
                markup("10", ChargeComponent.FREIGHT), BigDecimal.ZERO);

        assertThat(p.markupAmount()).isEqualByComparingTo("33.34"); // 33.335
        assertThat(p.total()).isEqualByComparingTo("372.69");       // 333.35 + 33.34 + 6.00
    }

    @Test
    @DisplayName("a disabled rule or no markup leaves the provider price untouched")
    void disabledOrNone() {
        Markup off = new Markup(null, false, new BigDecimal("10"), EnumSet.of(ChargeComponent.FREIGHT), new BigDecimal("18"));
        assertThat(MarkupCalculator.price(List.of(line(ChargeComponent.FREIGHT, "100")), off, null).total())
                .isEqualByComparingTo("100.00");
        assertThat(MarkupCalculator.price(List.of(line(ChargeComponent.FREIGHT, "100")), null, null).total())
                .isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("an untaxed rate card (OCCPL network) gets GST on its pre-tax total and no markup")
    void untaxedRateCard() {
        Priced p = MarkupCalculator.price(List.of(line(ChargeComponent.FREIGHT, "500"), line(ChargeComponent.HANDLING, "50")),
                null, new BigDecimal("18"));

        assertThat(p.taxAmount()).isEqualByComparingTo("99.00");
        assertThat(p.markupAmount()).isEqualByComparingTo("0.00");
        assertThat(p.total()).isEqualByComparingTo("649.00");
    }
}
