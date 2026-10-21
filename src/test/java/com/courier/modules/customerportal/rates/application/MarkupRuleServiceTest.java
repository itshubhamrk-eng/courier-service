package com.courier.modules.customerportal.rates.application;

import com.courier.modules.customerportal.rates.application.MarkupCalculator.Markup;
import com.courier.modules.customerportal.rates.domain.ChargeComponent;
import com.courier.modules.customerportal.rates.domain.PricingMarkupRule;
import com.courier.modules.customerportal.rates.domain.PricingMarkupRuleRepository;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarkupRuleServiceTest {

    private static final UUID COMPANY = UUID.randomUUID();
    private static final UUID CUSTOMER = UUID.randomUUID();
    private static final Instant NOW = Instant.now();

    @Mock private PricingMarkupRuleRepository repository;
    @Mock private AuditService auditService;
    private MarkupRuleService service;

    @BeforeEach
    void setUp() {
        service = new MarkupRuleService(repository, new CustomerPortalProperties(), auditService);
        CompanyContext.setCompanyId(COMPANY);
    }

    @AfterEach
    void clear() {
        CompanyContext.clear();
    }

    private static PricingMarkupRule rule(String provider, UUID customer, String pct, Instant from, Instant to) {
        PricingMarkupRule r = PricingMarkupRule.builder().providerCode(provider).customerId(customer).markupEnabled(true)
                .markupPercent(new BigDecimal(pct)).appliesTo("FREIGHT").effectiveFrom(from).effectiveTo(to).build();
        r.setId(UUID.randomUUID());
        return r;
    }

    @Test
    @DisplayName("no rule in force: the configured default of 10 % on freight")
    void defaultMarkup() {
        when(repository.findCandidates(COMPANY, "SHIPROCKET", CUSTOMER)).thenReturn(List.of());

        Markup m = service.resolve(COMPANY, "SHIPROCKET", CUSTOMER, NOW);

        assertThat(m.percent()).isEqualByComparingTo("10");
        assertThat(m.components()).containsExactly(ChargeComponent.FREIGHT);
        assertThat(m.ruleId()).isNull();
    }

    @Test
    @DisplayName("the most specific rule in force wins; expired and future rules are ignored")
    void mostSpecificInForce() {
        Instant past = NOW.minus(10, ChronoUnit.DAYS);
        PricingMarkupRule all = rule(null, null, "10", past, null);
        PricingMarkupRule provider = rule("SHIPROCKET", null, "12", past, null);
        PricingMarkupRule customerExpired = rule("SHIPROCKET", CUSTOMER, "5", past, NOW.minus(1, ChronoUnit.DAYS));
        PricingMarkupRule customerFuture = rule(null, CUSTOMER, "7", NOW.plus(1, ChronoUnit.DAYS), null);
        when(repository.findCandidates(COMPANY, "SHIPROCKET", CUSTOMER))
                .thenReturn(List.of(all, provider, customerExpired, customerFuture));

        assertThat(service.resolve(COMPANY, "SHIPROCKET", CUSTOMER, NOW).percent()).isEqualByComparingTo("12");
    }

    @Test
    @DisplayName("a new rule closes the open rule of the same scope at its start")
    void closesPrevious() {
        PricingMarkupRule open = rule(null, null, "10", NOW.minus(30, ChronoUnit.DAYS), null);
        when(repository.findAllWithinCompany(COMPANY)).thenReturn(List.of(open));
        when(repository.save(any(PricingMarkupRule.class))).thenAnswer(i -> i.getArgument(0));
        Instant start = NOW.plus(1, ChronoUnit.HOURS);

        PricingMarkupRule created = service.create(new MarkupRuleService.CreateCommand(null, null, true,
                new BigDecimal("12.5"), Set.of(ChargeComponent.FREIGHT, ChargeComponent.FUEL_SURCHARGE), start, null, "festive"));

        assertThat(open.getEffectiveTo()).isEqualTo(start);
        assertThat(created.getAppliesTo()).isEqualTo("FREIGHT,FUEL_SURCHARGE");
        assertThat(created.getMarkupPercent()).isEqualByComparingTo("12.5");
    }

    @Test
    @DisplayName("tax is never marked up, and the percentage stays within 0-100")
    void validation() {
        assertThatThrownBy(() -> service.create(new MarkupRuleService.CreateCommand(null, null, true,
                BigDecimal.TEN, Set.of(ChargeComponent.TAX), null, null, null))).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.create(new MarkupRuleService.CreateCommand(null, null, true,
                new BigDecimal("101"), Set.of(ChargeComponent.FREIGHT), null, null, null))).isInstanceOf(BusinessRuleException.class);
    }
}
