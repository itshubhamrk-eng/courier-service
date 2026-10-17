package com.courier.modules.auth.application;

import com.courier.modules.auth.domain.Role;
import com.courier.modules.auth.domain.User;
import com.courier.modules.auth.domain.UserRepository;
import com.courier.modules.master.domain.GlobalMasters;
import com.courier.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuperAdminBootstrapTest {

    @Mock UserRepository userRepository;
    @Mock UserProvisioningService provisioning;

    BootstrapProperties properties;
    SuperAdminBootstrap bootstrap;

    @BeforeEach
    void setUp() {
        properties = new BootstrapProperties();
        bootstrap = new SuperAdminBootstrap(properties, userRepository, provisioning);
    }

    private void run() {
        bootstrap.run(new DefaultApplicationArguments());
    }

    @Test
    void doesNothingWhenNoEmailIsConfigured() {
        run();
        verify(provisioning, never()).provisionSuperAdmin(any());
    }

    @Test
    void createsTheSuperAdminAnchoredToThePlatformCompany() {
        properties.setSuperAdminEmail("Owner@Example.com ");
        properties.setSuperAdminPassword("S3cure-Passw0rd!");
        when(userRepository.findPlatformUsersByEmail(any(String.class), any(Collection.class))).thenReturn(List.of());

        run();

        ArgumentCaptor<UserProvisioningService.NewSuperAdminCommand> cmd =
                ArgumentCaptor.forClass(UserProvisioningService.NewSuperAdminCommand.class);
        verify(provisioning).provisionSuperAdmin(cmd.capture());
        assertThat(cmd.getValue().companyId()).isEqualTo(GlobalMasters.PLATFORM_COMPANY_ID);
        assertThat(cmd.getValue().email()).isEqualTo("owner@example.com");
        assertThat(cmd.getValue().password()).isEqualTo("S3cure-Passw0rd!");
    }

    @Test
    void leavesAnExistingPlatformOperatorAlone() {
        properties.setSuperAdminEmail("owner@example.com");
        properties.setSuperAdminPassword("S3cure-Passw0rd!");
        when(userRepository.findPlatformUsersByEmail(any(String.class), any(Collection.class)))
                .thenReturn(List.of(User.builder().email("owner@example.com").roles(java.util.EnumSet.of(Role.SUPER_ADMIN)).build()));

        run();

        verify(provisioning, never()).provisionSuperAdmin(any());
    }

    @Test
    void skipsWhenThereIsNoPassword() {
        properties.setSuperAdminEmail("owner@example.com");
        when(userRepository.findPlatformUsersByEmail(any(String.class), any(Collection.class))).thenReturn(List.of());

        run();

        verify(provisioning, never()).provisionSuperAdmin(any());
    }

    @Test
    void doesNotStopTheAppWhenThePasswordIsRejected() {
        properties.setSuperAdminEmail("owner@example.com");
        properties.setSuperAdminPassword("weak");
        when(userRepository.findPlatformUsersByEmail(any(String.class), any(Collection.class))).thenReturn(List.of());
        when(provisioning.provisionSuperAdmin(any())).thenThrow(new BusinessRuleException("Password too short"));

        assertThatCode(this::run).doesNotThrowAnyException();
    }
}
