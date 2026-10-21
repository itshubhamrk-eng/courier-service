package com.courier.modules.customerportal.rates.application;

import com.courier.modules.customerportal.application.ActingCustomer;
import com.courier.modules.customerportal.rates.domain.RateQuote;
import com.courier.modules.customerportal.rates.domain.RateQuoteOption;
import com.courier.modules.customerportal.rates.domain.RateQuoteOptionRepository;
import com.courier.modules.customerportal.rates.domain.RateQuoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persists comparisons in one short transaction, after the (slow) provider calls are over. */
@Component
@RequiredArgsConstructor
public class RateQuoteStore {

    private final RateQuoteRepository quotes;
    private final RateQuoteOptionRepository options;

    @Transactional
    public Saved save(RateQuote quote, List<RateQuoteOption> rows) {
        RateQuote saved = quotes.save(quote);
        rows.forEach(o -> o.setQuoteId(saved.getId()));
        return new Saved(saved, options.saveAll(rows));
    }

    @Transactional(readOnly = true)
    public Optional<Saved> load(UUID quoteId, ActingCustomer acting) {
        return quotes.findOwned(quoteId, acting.companyId(), acting.customerId())
                .map(q -> new Saved(q, options.findByQuote(q.getId(), acting.companyId())));
    }

    public record Saved(RateQuote quote, List<RateQuoteOption> options) {
    }
}
