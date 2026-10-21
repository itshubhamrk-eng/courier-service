package com.courier.shared.config;

import com.courier.shared.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CustomerScopeFilterTest {

    private final CustomerScopeFilter filter = new CustomerScopeFilter(new ObjectMapper().findAndRegisterModules());

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void signIn(Set<String> roles) {
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), "u@x.test", roles, "jti");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.authorities()));
    }

    private static MockHttpServletResponse run(CustomerScopeFilter filter, String uri, FilterChain chain) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/customers", "/api/v1/shipments", "/api/v1/customer-rates/calculate",
            "/api/v1/users", "/api/v1/global-masters/pincodes", "/api/v1/customer-portalx/profile"})
    @DisplayName("a customer account is refused everywhere outside the portal and auth")
    void customerRefusedOutsidePortal(String uri) throws Exception {
        signIn(Set.of("CUSTOMER"));
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletResponse response = run(filter, uri, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("customer portal");
        verify(chain, never()).doFilter(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/customer-portal/profile", "/api/v1/auth/refresh", "/api/v1/auth/logout"})
    @DisplayName("a customer account reaches the portal and auth endpoints")
    void customerAllowedInPortal(String uri) throws Exception {
        signIn(Set.of("CUSTOMER"));
        FilterChain chain = mock(FilterChain.class);

        run(filter, uri, chain);

        verify(chain).doFilter(any(), any());
    }

    @Test
    @DisplayName("fail-closed: CUSTOMER together with a staff role is still kept inside the portal")
    void mixedRolesStillGated() throws Exception {
        signIn(Set.of("CUSTOMER", "COMPANY_ADMIN"));
        FilterChain chain = mock(FilterChain.class);

        assertThat(run(filter, "/api/v1/customers", chain).getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("staff accounts are untouched")
    void staffUntouched() throws Exception {
        signIn(Set.of("COMPANY_ADMIN"));
        FilterChain chain = mock(FilterChain.class);

        run(filter, "/api/v1/customers", chain);

        verify(chain).doFilter(any(), any());
    }

    @Test
    @DisplayName("anonymous requests are left to the normal rules")
    void anonymousUntouched() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        run(filter, "/api/v1/track/ABC", chain);

        verify(chain).doFilter(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/customer-portal/../customers", "/api/v1/customer-portal//x",
            "/api/v1/customer-portal/%2e%2e/customers", "/api/v1/customer-portal;x/profile"})
    @DisplayName("path tricks under an allowed prefix are refused")
    void pathTricksRefused(String uri) {
        assertThat(CustomerScopeFilter.allowed(uri)).isFalse();
    }
}
