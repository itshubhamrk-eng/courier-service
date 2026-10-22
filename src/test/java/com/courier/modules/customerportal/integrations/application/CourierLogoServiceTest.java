package com.courier.modules.customerportal.integrations.application;

import com.courier.modules.customerportal.integrations.domain.CourierIntegration;
import com.courier.modules.customerportal.integrations.domain.CourierIntegrationRepository;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CourierLogoServiceTest {

    private static final UUID COMPANY = UUID.randomUUID();
    private final CourierIntegrationRepository repository = mock(CourierIntegrationRepository.class);
    private CourierLogoService service;

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};

    @BeforeEach
    void setUp() {
        service = new CourierLogoService(repository, mock(AuditService.class));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        CompanyContext.setCompanyId(COMPANY);
        AuthenticatedUser admin = new AuthenticatedUser(UUID.randomUUID(), COMPANY, "admin@shop.test", Set.of("COMPANY_ADMIN"), "jti");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(admin, null, admin.authorities()));
    }

    @AfterEach
    void clear() {
        CompanyContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("a PNG is stored as a data URL on the courier's row, created if needed")
    void uploads() {
        when(repository.findByProvider(COMPANY, "DELHIVERY")).thenReturn(Optional.empty());
        service.upload("DELHIVERY", PNG);
        org.mockito.ArgumentCaptor<CourierIntegration> saved = org.mockito.ArgumentCaptor.forClass(CourierIntegration.class);
        org.mockito.Mockito.verify(repository).save(saved.capture());
        assertThat(saved.getValue().getLogoDataUrl()).startsWith("data:image/png;base64,");
        assertThat(saved.getValue().isEnabled()).isFalse();
    }

    @Test
    @DisplayName("the type comes from the file's bytes: SVG, HTML and a renamed file are refused")
    void refusesOtherTypes() {
        assertThatThrownBy(() -> service.upload("DELHIVERY", "<svg onload=alert(1)/>".getBytes()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("PNG, JPEG or WebP");
        assertThatThrownBy(() -> service.upload("DELHIVERY", "GIF89a....".getBytes()))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("an empty or oversized file is refused")
    void refusesSize() {
        assertThatThrownBy(() -> service.upload("DELHIVERY", new byte[0])).isInstanceOf(BusinessRuleException.class);
        byte[] big = new byte[com.courier.shared.image.LogoImages.MAX_BYTES + 1];
        System.arraycopy(PNG, 0, big, 0, PNG.length);
        assertThatThrownBy(() -> service.upload("DELHIVERY", big)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("150 KB");
    }

    @Test
    @DisplayName("an unknown courier is a 404, not a new row")
    void unknownCourier() {
        assertThatThrownBy(() -> service.upload("NOPE", PNG)).isInstanceOf(com.courier.shared.exception.ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("the portal reads only rows that have a logo")
    void portalRead() {
        CourierIntegration with = new CourierIntegration();
        with.setProviderCode("DELHIVERY");
        with.setLogoDataUrl("data:image/png;base64,AAA");
        CourierIntegration without = new CourierIntegration();
        without.setProviderCode("SHIPROCKET");
        when(repository.findAllOfCompany(eq(COMPANY))).thenReturn(java.util.List.of(with, without));
        assertThat(service.logosOfCompany()).containsOnlyKeys("DELHIVERY");
    }
}
