package com.courier.modules.customerportal.application;

import com.courier.modules.auth.application.CustomerPortalAccountService;
import com.courier.modules.customer.application.CustomerPortalLookup;
import com.courier.modules.customer.domain.Customer;
import com.courier.shared.exception.ForbiddenException;
import com.courier.shared.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentCustomerResolverTest {

    private static final UUID COMPANY = UUID.randomUUID();
    private static final UUID OTHER_COMPANY = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID CUSTOMER = UUID.randomUUID();

    @Mock private CustomerPortalAccountService accounts;
    @Mock private CustomerPortalLookup customers;
    @InjectMocks private CurrentCustomerResolver resolver;

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void signIn(UUID company, Set<String> roles) {
        AuthenticatedUser user = new AuthenticatedUser(USER, company, "buyer@shop.test", roles, "jti");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.authorities()));
    }

    @Test
    @DisplayName("resolves the customer from the signed-in account")
    void resolves() {
        signIn(COMPANY, Set.of("CUSTOMER"));
        when(accounts.activeCustomerIdOf(USER, COMPANY)).thenReturn(Optional.of(CUSTOMER));
        when(customers.findActiveBusinessCustomer(COMPANY, CUSTOMER)).thenReturn(Optional.of(new Customer()));

        assertThat(resolver.require()).isEqualTo(new ActingCustomer(COMPANY, USER, CUSTOMER));
    }

    @Test
    @DisplayName("a staff account is not a portal customer")
    void staffRefused() {
        signIn(COMPANY, Set.of("COMPANY_ADMIN"));

        assertThatThrownBy(resolver::require).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(accounts, customers);
    }

    @Test
    @DisplayName("a disabled or unlinked account is refused")
    void inactiveAccountRefused() {
        signIn(COMPANY, Set.of("CUSTOMER"));
        when(accounts.activeCustomerIdOf(USER, COMPANY)).thenReturn(Optional.empty());

        assertThatThrownBy(resolver::require).isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("an inactive, non-business or other-company customer is refused")
    void customerNotEligible() {
        signIn(OTHER_COMPANY, Set.of("CUSTOMER"));
        when(accounts.activeCustomerIdOf(USER, OTHER_COMPANY)).thenReturn(Optional.of(CUSTOMER));
        when(customers.findActiveBusinessCustomer(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(resolver::require).isInstanceOf(ForbiddenException.class);
    }
}
