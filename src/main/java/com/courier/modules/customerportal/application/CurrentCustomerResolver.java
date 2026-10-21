package com.courier.modules.customerportal.application;

import com.courier.modules.auth.application.CustomerPortalAccountService;
import com.courier.modules.customer.application.CustomerPortalLookup;
import com.courier.shared.exception.ErrorCode;
import com.courier.shared.exception.ForbiddenException;
import com.courier.shared.security.AuthenticatedUser;
import com.courier.shared.security.Roles;
import com.courier.shared.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Resolves the business customer a portal request acts for, on every request:
 * verified principal -> {@code users.customer_id} (read fresh, account must be ACTIVE) ->
 * an ACTIVE business customer of the same company. Any gap is 403 — a portal endpoint never
 * accepts a customer id from the browser.
 */
@Component
@RequiredArgsConstructor
public class CurrentCustomerResolver {

    static final String NO_ACCESS = "This account has no access to the customer portal.";

    private final CustomerPortalAccountService accounts;
    private final CustomerPortalLookup customers;

    public ActingCustomer require() {
        AuthenticatedUser user = SecurityUtils.requireCurrentUser();
        if (!user.hasRole(Roles.CUSTOMER) || user.companyId() == null) {
            throw new ForbiddenException(ErrorCode.ACCESS_DENIED, NO_ACCESS);
        }
        UUID customerId = accounts.activeCustomerIdOf(user.userId(), user.companyId())
                .orElseThrow(() -> new ForbiddenException(ErrorCode.ACCESS_DENIED, NO_ACCESS));
        customers.findActiveBusinessCustomer(user.companyId(), customerId)
                .orElseThrow(() -> new ForbiddenException(ErrorCode.ACCESS_DENIED, NO_ACCESS));
        return new ActingCustomer(user.companyId(), user.userId(), customerId);
    }
}
