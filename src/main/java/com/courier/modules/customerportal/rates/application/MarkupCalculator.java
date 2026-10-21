package com.courier.modules.customerportal.rates.application;

import com.courier.modules.customerportal.rates.domain.ChargeComponent;
import com.courier.modules.customerportal.rates.domain.ChargeLine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Prices one provider service for the customer. Pure arithmetic, BigDecimal, HALF_UP to paise.
 *
 * <pre>
 * markup base   = provider charge lines whose component is in the rule's list (never TAX)
 * markup        = base x percent / 100
 * markup tax    = markup x markupGstPercent / 100   (OCCPL's own service; provider tax is left as given)
 * customer total= provider total (incl. provider tax) + markup + markup tax
 * </pre>
 * For OCCPL's own network there is no markup; tax is added on the pre-tax rate card total when the
 * provider lines carry no TAX line.
 */
public final class MarkupCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private MarkupCalculator() {
    }

    public static Priced price(List<ChargeLine> providerLines, Markup markup, BigDecimal taxPercentIfUntaxed) {
        List<ChargeLine> lines = new ArrayList<>(providerLines.stream()
                .map(l -> new ChargeLine(l.code(), l.label(), l.component(), money(l.amount()))).toList());
        BigDecimal providerTotal = sum(lines);
        BigDecimal baseFreight = sum(lines.stream().filter(l -> l.component() == ChargeComponent.FREIGHT).toList());
        BigDecimal providerTax = sum(lines.stream().filter(l -> l.component() == ChargeComponent.TAX).toList());
        BigDecimal other = providerTotal.subtract(baseFreight).subtract(providerTax);

        BigDecimal tax = providerTax;
        if (providerTax.signum() == 0 && taxPercentIfUntaxed != null && taxPercentIfUntaxed.signum() > 0) {
            BigDecimal gst = percent(providerTotal, taxPercentIfUntaxed);
            lines.add(new ChargeLine("GST", "GST " + plain(taxPercentIfUntaxed) + "%", ChargeComponent.TAX, gst));
            tax = tax.add(gst);
        }

        BigDecimal markupBase = BigDecimal.ZERO.setScale(2);
        BigDecimal markupAmount = BigDecimal.ZERO.setScale(2);
        BigDecimal markupTax = BigDecimal.ZERO.setScale(2);
        if (markup != null && markup.enabled() && markup.percent().signum() > 0) {
            markupBase = sum(lines.stream().filter(l -> markup.components().contains(l.component())
                    && l.component() != ChargeComponent.TAX && !l.code().equals("GST")).toList());
            markupAmount = percent(markupBase, markup.percent());
            lines.add(new ChargeLine("OCCPL_SERVICE", "OCCPL service charge (" + plain(markup.percent()) + "%)",
                    ChargeComponent.OTHER, markupAmount));
            if (markup.gstPercent() != null && markup.gstPercent().signum() > 0 && markupAmount.signum() > 0) {
                markupTax = percent(markupAmount, markup.gstPercent());
                lines.add(new ChargeLine("OCCPL_SERVICE_GST", "GST on service charge (" + plain(markup.gstPercent()) + "%)",
                        ChargeComponent.TAX, markupTax));
            }
        }
        BigDecimal total = sum(lines);
        return new Priced(baseFreight, other, markup == null ? null : markup.percent(), markupBase, markupAmount,
                tax.add(markupTax), total, List.copyOf(lines));
    }

    static BigDecimal percent(BigDecimal amount, BigDecimal pct) {
        return money(amount.multiply(pct).divide(HUNDRED, 8, RoundingMode.HALF_UP));
    }

    static BigDecimal money(BigDecimal v) {
        return v == null ? BigDecimal.ZERO.setScale(2) : v.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal sum(List<ChargeLine> lines) {
        return lines.stream().map(ChargeLine::amount).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    private static String plain(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }

    /** The markup that applies: percent, components, and the GST charged on it. */
    public record Markup(java.util.UUID ruleId, boolean enabled, BigDecimal percent, Set<ChargeComponent> components,
                         BigDecimal gstPercent) {
    }

    public record Priced(BigDecimal baseFreight, BigDecimal otherCharges, BigDecimal markupPercent, BigDecimal markupBase,
                         BigDecimal markupAmount, BigDecimal taxAmount, BigDecimal total, List<ChargeLine> lines) {
    }
}
