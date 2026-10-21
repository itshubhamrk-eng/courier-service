package com.courier.modules.customerportal.application;

import com.courier.modules.auth.application.CustomerPortalAccountService;
import com.courier.modules.auth.application.CustomerPortalAccountService.CustomerPortalAccount;
import com.courier.modules.auth.application.UserProvisioningService;
import com.courier.modules.auth.application.UserProvisioningService.NewCustomerPortalUserCommand;
import com.courier.modules.auth.application.UserProvisioningService.ProvisionedUser;
import com.courier.modules.customer.application.CustomerPortalLookup;
import com.courier.modules.customer.domain.Customer;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.security.Roles;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Company admin side: give a business customer a portal account, see it, take it away.
 * Only ACTIVE business customers of the admin's own company qualify.
 */
@Service
@RequiredArgsConstructor
public class CustomerPortalAccessService {

    private static final String COMPANY_ADMIN = "hasRole('" + Roles.COMPANY_ADMIN + "')";

    private final CustomerPortalLookup customers;
    private final UserProvisioningService provisioning;
    private final CustomerPortalAccountService accounts;

    @Transactional
    @PreAuthorize(COMPANY_ADMIN)
    public ProvisionedUser grant(UUID customerId, GrantCommand command) {
        UUID companyId = CompanyContext.requireCompanyId();
        Customer customer = requireBusinessCustomer(companyId, customerId);
        String email = blankToNull(command.email()) != null ? command.email() : customer.getEmail();
        if (blankToNull(email) == null) {
            throw new BusinessRuleException("An email address is required: the customer has none on record.");
        }
        String firstName = blankToNull(command.firstName()) != null ? command.firstName() : customer.getContactPerson();
        return provisioning.provisionCustomerPortalUser(new NewCustomerPortalUserCommand(
                companyId, customerId, email.trim(), firstName, command.lastName(), customer.getMobile()));
    }

    @Transactional(readOnly = true)
    @PreAuthorize(COMPANY_ADMIN)
    public Optional<CustomerPortalAccount> find(UUID customerId) {
        return accounts.findByCustomer(CompanyContext.requireCompanyId(), customerId);
    }

    @Transactional
    @PreAuthorize(COMPANY_ADMIN)
    public CustomerPortalAccount revoke(UUID customerId) {
        return accounts.disable(CompanyContext.requireCompanyId(), customerId);
    }

    private Customer requireBusinessCustomer(UUID companyId, UUID customerId) {
        return customers.findActiveBusinessCustomer(companyId, customerId)
                .orElseThrow(() -> new BusinessRuleException(
                        "Portal access is available only for an active business customer of this company."));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public record GrantCommand(String email, String firstName, String lastName) {
    }
}
