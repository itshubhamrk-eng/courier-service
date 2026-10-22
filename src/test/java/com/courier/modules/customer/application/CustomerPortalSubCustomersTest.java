package com.courier.modules.customer.application;

import com.courier.modules.customer.application.CustomerPortalSubCustomers.SubCustomerCommand;
import com.courier.modules.customer.domain.Customer;
import com.courier.modules.customer.domain.CustomerRepository;
import com.courier.modules.customer.domain.CustomerType;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerPortalSubCustomersTest {

    private static final UUID COMPANY = UUID.randomUUID();
    private static final UUID PARENT = UUID.randomUUID();
    private final CustomerRepository repository = mock(CustomerRepository.class);
    private CustomerPortalSubCustomers service;

    private static SubCustomerCommand command(String mobile) {
        return new SubCustomerCommand("Meera", "Joshi", null, mobile, "meera@x.test", null, null);
    }

    @BeforeEach
    void setUp() {
        service = new CustomerPortalSubCustomers(repository, mock(AuditService.class));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(repository.findChildren(COMPANY, PARENT)).thenReturn(List.of());
        when(repository.findByCompanyIdAndMobile(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("a registered customer carries the registering customer as its parent and gets a code")
    void createCarriesParent() {
        Customer c = service.create(COMPANY, PARENT, command("9876500001"));
        assertThat(c.getParentCustomerId()).isEqualTo(PARENT);
        assertThat(c.getCustomerCode()).startsWith("CUST");
        assertThat(c.getCustomerType()).isEqualTo(CustomerType.INDIVIDUAL);
    }

    @Test
    @DisplayName("an invalid or already used mobile number is refused")
    void mobileRules() {
        assertThatThrownBy(() -> service.create(COMPANY, PARENT, command("abc"))).isInstanceOf(BusinessRuleException.class);
        when(repository.findByCompanyIdAndMobile(COMPANY, "9876500001")).thenReturn(Optional.of(Customer.builder().build()));
        assertThatThrownBy(() -> service.create(COMPANY, PARENT, command("9876500001")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("already exists");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("a customer registered by someone else, or by staff, is not found")
    void ownership() {
        UUID id = UUID.randomUUID();
        Customer other = Customer.builder().parentCustomerId(UUID.randomUUID()).build();
        when(repository.findByIdWithinCompany(id, COMPANY)).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.get(COMPANY, PARENT, id)).isInstanceOf(ResourceNotFoundException.class);
        Customer staffMade = Customer.builder().build();
        when(repository.findByIdWithinCompany(id, COMPANY)).thenReturn(Optional.of(staffMade));
        assertThatThrownBy(() -> service.remove(COMPANY, PARENT, id)).isInstanceOf(ResourceNotFoundException.class);
    }
}
