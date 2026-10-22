package com.courier.modules.customer.application;

import com.courier.modules.customer.domain.Customer;
import com.courier.modules.customer.domain.CustomerRepository;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerLogoServiceTest {

    private static final UUID COMPANY = UUID.randomUUID();
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
    private final CustomerRepository repository = mock(CustomerRepository.class);
    private CustomerLogoService service;

    @BeforeEach
    void setUp() {
        service = new CustomerLogoService(repository, mock(AuditService.class));
        CompanyContext.setCompanyId(COMPANY);
    }

    @AfterEach
    void clear() {
        CompanyContext.clear();
    }

    @Test
    @DisplayName("a valid image is stored as a data URL for a customer of the company")
    void uploads() {
        UUID id = UUID.randomUUID();
        when(repository.setLogo(eq(id), eq(COMPANY), any())).thenReturn(1);
        service.upload(id, PNG);
        verify(repository).setLogo(eq(id), eq(COMPANY), org.mockito.ArgumentMatchers.startsWith("data:image/png;base64,"));
    }

    @Test
    @DisplayName("a customer of another company is a 404")
    void otherCompany() {
        UUID id = UUID.randomUUID();
        when(repository.setLogo(eq(id), eq(COMPANY), any())).thenReturn(0);
        assertThatThrownBy(() -> service.upload(id, PNG)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("HTML or SVG is refused before the database is touched")
    void refusesNonImages() {
        assertThatThrownBy(() -> service.upload(UUID.randomUUID(), "<svg/>".getBytes())).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("removing clears the logo; reading returns it or empty")
    void removeAndRead() {
        UUID id = UUID.randomUUID();
        when(repository.setLogo(eq(id), eq(COMPANY), isNull())).thenReturn(1);
        service.remove(id);
        Customer c = Customer.builder().build();
        when(repository.findByIdWithinCompany(id, COMPANY)).thenReturn(Optional.of(c));
        assertThat(service.get(id)).isEmpty();
        c.setLogoDataUrl("data:image/png;base64,AA==");
        assertThat(service.get(id)).contains("data:image/png;base64,AA==");
    }
}
