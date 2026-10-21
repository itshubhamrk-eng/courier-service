package com.courier.modules.auth.application;

import com.courier.modules.auth.domain.Role;
import com.courier.modules.auth.domain.User;
import com.courier.modules.auth.domain.UserRepository;
import com.courier.modules.auth.domain.UserStatus;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The customer-portal side of {@code users}: which account acts for which business
 * customer (V128 {@code customer_id}). Creation is {@link UserProvisioningService#provisionCustomerPortalUser};
 * this class reads and disables. Authorization of the caller is the calling module's job
 * ({@code customerportal}), like every other cross-module application service here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerPortalAccountService {

    private final UserRepository userRepository;
    private final SessionService sessionService;
    private final AuditService auditService;

    /** The account of one business customer, in any status. */
    @Transactional(readOnly = true)
    public Optional<CustomerPortalAccount> findByCustomer(UUID companyId, UUID customerId) {
        return userRepository.findByCustomerIdWithinCompany(customerId, companyId).map(CustomerPortalAccountService::view);
    }

    /**
     * The customer an authenticated user acts for — only when the account is a customer
     * account that may still be used (ACTIVE; PENDING cannot have signed in). Read fresh from
     * the database on every call, so disabling an account takes effect immediately.
     */
    @Transactional(readOnly = true)
    public Optional<UUID> activeCustomerIdOf(UUID userId, UUID companyId) {
        return userRepository.findByIdWithinCompany(userId, companyId)
                .filter(u -> u.getRoles().contains(Role.CUSTOMER))
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .map(User::getCustomerId);
    }

    /** Disables the customer's account and ends every session it has. */
    @Transactional
    public CustomerPortalAccount disable(UUID companyId, UUID customerId) {
        User user = userRepository.findByCustomerIdWithinCompany(customerId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer portal account", customerId));
        if (user.getStatus() != UserStatus.DISABLED) {
            user.setStatus(UserStatus.DISABLED);
            userRepository.save(user);
            sessionService.revokeAllSessions(user.getId(), "CUSTOMER_PORTAL_ACCESS_REVOKED");
            auditService.record(AuditAction.USER_DEACTIVATED, "User", user.getId(),
                    Map.of("customerId", customerId.toString(), "reason", "customer portal access revoked"));
            log.info("Disabled customer portal user {} of customer {}", user.getId(), customerId);
        }
        return view(user);
    }

    private static CustomerPortalAccount view(User u) {
        return new CustomerPortalAccount(u.getId(), u.getEmail(), u.getStatus().name(), u.getLastLoginAt());
    }

    public record CustomerPortalAccount(UUID userId, String email, String status, Instant lastLoginAt) {
    }
}
