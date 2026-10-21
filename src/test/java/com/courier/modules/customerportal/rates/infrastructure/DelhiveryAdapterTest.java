package com.courier.modules.customerportal.rates.infrastructure;

import com.courier.modules.customerportal.rates.application.CustomerPortalProperties;
import com.courier.modules.customerportal.rates.domain.ChargeComponent;
import com.courier.modules.customerportal.rates.domain.ProviderResult;
import com.courier.modules.customerportal.rates.domain.RateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DelhiveryAdapterTest {

    private MockRestServiceServer server;
    private DelhiveryAdapter adapter;

    @BeforeEach
    void setUp() {
        CustomerPortalProperties.Delhivery cfg = new CustomerPortalProperties.Delhivery();
        cfg.setEnabled(true);
        cfg.setBaseUrl("https://dl.test");
        cfg.setToken("tok");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new DelhiveryAdapter(builder, cfg, 5000);
    }

    private static RateRequest request() {
        // 40 x 30 x 20 cm, 2 packages: volumetric 9.6 kg beats 3 kg actual
        return new RateRequest(UUID.randomUUID(), UUID.randomUUID(), "411016", "110001", new BigDecimal("3"),
                new BigDecimal("40"), new BigDecimal("30"), new BigDecimal("20"), 2, new BigDecimal("2000"),
                RateRequest.PaymentMode.PREPAID);
    }

    @Test
    @DisplayName("checks the pincode, prices Surface and Express on the chargeable weight, splits Delhivery's GST out")
    void pricesBothModes() {
        server.expect(requestTo(startsWith("https://dl.test/c/api/pin-codes/json/")))
                .andExpect(header("Authorization", "Token tok"))
                .andRespond(withSuccess("{\"delivery_codes\":[{\"postal_code\":{\"pin\":110001,\"cod\":\"Y\"}}]}", MediaType.APPLICATION_JSON));
        for (String md : new String[]{"S", "E"}) {
            server.expect(requestTo(startsWith("https://dl.test/api/kinko/v1/invoice/charges/.json")))
                    .andExpect(queryParam("md", md))
                    .andExpect(queryParam("cgm", "9600"))
                    .andRespond(withSuccess("[{\"total_amount\":" + ("S".equals(md) ? "236" : "354")
                            + ",\"gross_amount\":" + ("S".equals(md) ? "200" : "300") + ",\"charged_weight\":9600}]",
                            MediaType.APPLICATION_JSON));
        }

        ProviderResult result = adapter.getRates(request());

        assertThat(result.available()).isTrue();
        assertThat(result.rates()).hasSize(2);
        assertThat(result.rates().get(0).charges()).extracting(l -> l.component())
                .containsExactly(ChargeComponent.FREIGHT, ChargeComponent.TAX);
        assertThat(result.rates().get(0).providerTotal()).isEqualByComparingTo("236");
        assertThat(result.rates().get(0).chargeableWeightKg()).isEqualByComparingTo("9.6");
        server.verify();
    }

    @Test
    @DisplayName("a pincode Delhivery does not serve is reported as such")
    void notServiceable() {
        server.expect(requestTo(startsWith("https://dl.test/c/api/pin-codes/json/")))
                .andRespond(withSuccess("{\"delivery_codes\":[]}", MediaType.APPLICATION_JSON));

        ProviderResult result = adapter.getRates(request());

        assertThat(result.available()).isFalse();
        assertThat(result.reason()).contains("does not deliver");
    }
}
