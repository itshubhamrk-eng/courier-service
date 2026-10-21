package com.courier.modules.customerportal.application;

import com.courier.modules.auth.application.CustomerPortalAccountService;
import com.courier.modules.auth.application.UserProvisioningService;
import com.courier.modules.auth.application.UserProvisioningService.NewCustomerPortalUserCommand;
import com.courier.modules.customer.application.CustomerPortalLookup;
import com.courier.modules.customer.domain.Customer;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerPortalAccessServiceTest {

    private static final UUID COMPANY = UUID.randomUUID();
    private static final UUID CUSTOMER = UUID.randomUUID();

    @Mock private CustomerPortalLookup customers;
    @Mock private UserProvisioningService provisioning;
    @Mock private CustomerPortalAccountService accounts;
    @InjectMocks private CustomerPortalAccessService service;

    @BeforeEach
    void company() {
        CompanyContext.setCompanyId(COMPANY);
    }

    @AfterEach
    void clear() {
        CompanyContext.clear();
    }

    private static Customer business(String email) {
        Customer c = new Customer();
        c.setEmail(email);
        c.setContactPerson("Asha");
        c.setMobile("9876543210");
        return c;
    }

    @Test
    @DisplayName("defaults the email and first name from the customer record")
    void defaultsFromCustomer() {
        when(customers.findActiveBusinessCustomer(COMPANY, CUSTOMER)).thenReturn(Optional.of(business("shop@x.test")));

        service.grant(CUSTOMER, new CustomerPortalAccessService.GrantCommand(null, null, null));

        ArgumentCaptor<NewCustomerPortalUserCommand> captor = ArgumentCaptor.forClass(NewCustomerPortalUserCommand.class);
        verify(provisioning).provisionCustomerPortalUser(captor.capture());
        assertThat(captor.getValue()).isEqualTo(
                new NewCustomerPortalUserCommand(COMPANY, CUSTOMER, "shop@x.test", "Asha", null, "9876543210"));
    }

    @Test
    @DisplayName("only an active business customer of this company qualifies")
    void refusesOtherCustomers() {
        when(customers.findActiveBusinessCustomer(COMPANY, CUSTOMER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.grant(CUSTOMER, new CustomerPortalAccessService.GrantCommand("a@b.test", null, null)))
                .isInstanceOf(BusinessRuleException.class);
        verify(provisioning, never()).provisionCustomerPortalUser(any());
    }

    @Test
    @DisplayName("an email is required when the customer has none")
    void requiresEmail() {
        when(customers.findActiveBusinessCustomer(COMPANY, CUSTOMER)).thenReturn(Optional.of(business(null)));

        assertThatThrownBy(() -> service.grant(CUSTOMER, new CustomerPortalAccessService.GrantCommand(" ", null, null)))
                .isInstanceOf(BusinessRuleException.class);
    }
}
