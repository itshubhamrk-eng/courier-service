package com.courier.modules.auth.application;

import com.courier.modules.auth.domain.Role;
import com.courier.modules.auth.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

class AuthPropertiesLinkTest {

    private static User user(Role role) {
        return User.builder().email("u@x.test").roles(EnumSet.of(role)).build();
    }

    @Test
    @DisplayName("links for customer accounts point at the customer portal; staff links do not")
    void customerLinksGoToPortal() {
        AuthProperties properties = new AuthProperties();
        properties.setAppBaseUrl("https://courier.example");
        properties.setCustomerPortalBaseUrl("https://shop.example");

        assertThat(properties.linkBaseUrlFor(user(Role.CUSTOMER))).isEqualTo("https://shop.example");
        assertThat(properties.linkBaseUrlFor(user(Role.COMPANY_ADMIN))).isEqualTo("https://courier.example");
    }

    @Test
    @DisplayName("without a portal URL, customer links fall back to the app URL")
    void fallsBack() {
        AuthProperties properties = new AuthProperties();
        properties.setAppBaseUrl("https://courier.example");

        assertThat(properties.linkBaseUrlFor(user(Role.CUSTOMER))).isEqualTo("https://courier.example");
    }
}
