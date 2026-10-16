package com.courier.modules.auth.application;

import com.courier.modules.auth.domain.Role;
import com.courier.modules.auth.domain.User;
import com.courier.modules.auth.domain.UserRepository;
import com.courier.modules.master.domain.GlobalMasters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Creates the first platform operator (SUPER_ADMIN) on a fresh database, from two server settings, so nobody has to
 * write SQL or paste a password into a chat.
 *
 * <p>Off unless {@code app.bootstrap.super-admin-email} is set. It does nothing if that address already belongs to a
 * platform operator, so it is safe to leave configured; remove both settings once the account exists. The password is
 * checked against the normal password policy and is never logged.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.bootstrap", name = "super-admin-email")
public class SuperAdminBootstrap implements ApplicationRunner {

    private static final Set<Role> PLATFORM_ROLES = Set.of(Role.SUPER_ADMIN, Role.PLATFORM_ADMIN);

    private final BootstrapProperties properties;
    private final UserRepository userRepository;
    private final UserProvisioningService provisioning;

    @Override
    public void run(ApplicationArguments args) {
        String email = User.normaliseEmail(properties.getSuperAdminEmail());
        if (email == null || email.isBlank()) {
            return;
        }
        List<User> existing = userRepository.findPlatformUsersByEmail(email, PLATFORM_ROLES);
        if (!existing.isEmpty()) {
            log.info("Bootstrap super admin {} already exists; nothing to do (remove the bootstrap settings)", email);
            return;
        }
        String password = properties.getSuperAdminPassword();
        if (password == null || password.isBlank()) {
            log.warn("Bootstrap super admin {} not created: app.bootstrap.super-admin-password is not set", email);
            return;
        }
        try {
            provisioning.provisionSuperAdmin(new UserProvisioningService.NewSuperAdminCommand(
                    GlobalMasters.PLATFORM_COMPANY_ID, email, properties.getSuperAdminFirstName(),
                    properties.getSuperAdminLastName(), null, password));
            log.info("Created bootstrap super admin {}; remove the bootstrap settings now", email);
        } catch (RuntimeException e) {
            // Never stop the app from starting over this; say why, without the password.
            log.error("Bootstrap super admin {} was not created: {}", email, e.getMessage());
        }
    }
}
