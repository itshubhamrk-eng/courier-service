package com.courier.modules.customerportal.rates.infrastructure;

import com.courier.modules.customerportal.rates.application.CustomerPortalProperties;
import com.courier.modules.customerportal.rates.domain.BookingOutcome;
import com.courier.modules.customerportal.rates.domain.BookingRequest;
import com.courier.modules.customerportal.rates.domain.ChargeComponent;
import com.courier.modules.customerportal.rates.domain.ProviderRate;
import com.courier.modules.customerportal.rates.domain.ProviderResult;
import com.courier.modules.customerportal.rates.domain.RateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.startsWith;

class ShiprocketAdapterTest {

    private MockRestServiceServer server;
    private ShiprocketAdapter adapter;

    static RateRequest request(RateRequest.PaymentMode mode) {
        return new RateRequest(UUID.randomUUID(), UUID.randomUUID(), "411016", "110001", new BigDecimal("2.5"),
                new BigDecimal("30"), new BigDecimal("20"), new BigDecimal("10"), 1, new BigDecimal("1500"), mode);
    }

    @BeforeEach
    void setUp() {
        CustomerPortalProperties.Shiprocket cfg = new CustomerPortalProperties.Shiprocket();
        cfg.setEnabled(true);
        cfg.setBaseUrl("https://sr.test");
        cfg.setEmail("api@shop.test");
        cfg.setPassword("secret");
        cfg.setPickupLocation("OCCPL-PUNE");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new ShiprocketAdapter(builder, cfg);
    }

    @Test
    @DisplayName("logs in once, then maps every priced courier with its own charge lines")
    void mapsCouriers() {
        server.expect(requestTo("https://sr.test/v1/external/auth/login")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"token\":\"tkn\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith("https://sr.test/v1/external/courier/serviceability/")))
                .andExpect(header("Authorization", "Bearer tkn"))
                .andExpect(queryParam("cod", "1"))
                .andRespond(withSuccess("""
                        {"data":{"available_courier_companies":[
                          {"courier_company_id":10,"courier_name":"Xpress Air","rate":"250.40","freight_charge":"180","cod_charges":"40",
                           "estimated_delivery_days":"3","is_surface":false,"charge_weight":2.5},
                          {"courier_company_id":11,"courier_name":"No Price","rate":0}
                        ]}}""", MediaType.APPLICATION_JSON));

        ProviderResult result = adapter.getRates(request(RateRequest.PaymentMode.COD));

        assertThat(result.available()).isTrue();
        assertThat(result.rates()).hasSize(1);
        ProviderRate r = result.rates().get(0);
        assertThat(r.serviceName()).isEqualTo("Xpress Air");
        assertThat(r.transportMode()).isEqualTo("AIR");
        assertThat(r.etaMinDays()).isEqualTo(3);
        assertThat(r.providerTotal()).isEqualByComparingTo("250.40");
        assertThat(r.charges()).extracting(l -> l.component())
                .containsExactly(ChargeComponent.FREIGHT, ChargeComponent.COD, ChargeComponent.OTHER);
        server.verify();
    }

    @Test
    @DisplayName("a provider error becomes 'unavailable', never an exception or a made-up price")
    void providerError() {
        server.expect(requestTo("https://sr.test/v1/external/auth/login"))
                .andRespond(withSuccess("{\"token\":\"tkn\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith("https://sr.test/v1/external/courier/serviceability/")))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        ProviderResult result = adapter.getRates(request(RateRequest.PaymentMode.PREPAID));

        assertThat(result.available()).isFalse();
        assertThat(result.reason()).contains("Shiprocket");
    }

    @Test
    @DisplayName("without credentials nothing is called")
    void notConfigured() {
        ShiprocketAdapter off = new ShiprocketAdapter(RestClient.builder(), new CustomerPortalProperties.Shiprocket());

        assertThat(off.configured()).isFalse();
        assertThat(off.getRates(request(RateRequest.PaymentMode.PREPAID)).available()).isFalse();
    }

    static BookingRequest booking() {
        BookingRequest.Party p = new BookingRequest.Party("Ravi", "9876543211", "12 Connaught Place", "110001", "New Delhi", "Delhi");
        return new BookingRequest(UUID.randomUUID(), UUID.randomUUID(), "b-1", "10", request(RateRequest.PaymentMode.PREPAID),
                p, p, "Garments", new BigDecimal("111.80"));
    }

    private void login() {
        server.expect(requestTo("https://sr.test/v1/external/auth/login"))
                .andRespond(withSuccess("{\"token\":\"tkn\"}", MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("booking: order with our reference, then the chosen courier's AWB")
    void books() {
        login();
        server.expect(requestTo("https://sr.test/v1/external/orders/create/adhoc"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.order_id").value("b-1"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.pickup_location").value("OCCPL-PUNE"))
                .andRespond(withSuccess("{\"order_id\":555,\"shipment_id\":777}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://sr.test/v1/external/courier/assign/awb"))
                .andRespond(withSuccess("{\"response\":{\"data\":{\"awb_code\":\"SRAWB1\"}}}", MediaType.APPLICATION_JSON));

        BookingOutcome o = adapter.book(booking());

        assertThat(o.result()).isEqualTo(BookingOutcome.Result.BOOKED);
        assertThat(o.awbNumber()).isEqualTo("SRAWB1");
        assertThat(o.providerOrderRef()).isEqualTo("777");
    }

    @Test
    @DisplayName("booking: the order exists but the AWB step fails -> UNKNOWN with the Shiprocket reference, never retried")
    void halfBooked() {
        login();
        server.expect(requestTo("https://sr.test/v1/external/orders/create/adhoc"))
                .andRespond(withSuccess("{\"shipment_id\":777}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://sr.test/v1/external/courier/assign/awb"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        BookingOutcome o = adapter.book(booking());

        assertThat(o.result()).isEqualTo(BookingOutcome.Result.UNKNOWN);
        assertThat(o.providerOrderRef()).isEqualTo("777");
    }

    @Test
    @DisplayName("booking: a 4xx on the order is a definite rejection, a 5xx is unknown")
    void rejectedOrUnknown() {
        login();
        server.expect(requestTo("https://sr.test/v1/external/orders/create/adhoc")).andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY));
        assertThat(adapter.book(booking()).result()).isEqualTo(BookingOutcome.Result.REJECTED);

        server.reset();
        server.expect(requestTo("https://sr.test/v1/external/orders/create/adhoc")).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        assertThat(adapter.book(booking()).result()).isEqualTo(BookingOutcome.Result.UNKNOWN);
    }
}
