package com.courier.modules.customer.application;

import com.courier.modules.customer.domain.Customer;
import com.courier.modules.customer.domain.CustomerCodeGenerator;
import com.courier.modules.customer.domain.CustomerRepository;
import com.courier.modules.customer.domain.CustomerStatus;
import com.courier.modules.customer.domain.CustomerType;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ErrorCode;
import com.courier.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The customers a business customer registers for itself from the customer portal ({@code customerportal}). Each carries
 * the registering customer's id as {@code parentCustomerId}; it has its own address list like any customer.
 *
 * <p>No {@code @PreAuthorize}, like {@link CustomerPortalAddressBook}: the portal resolves company and customer from the
 * signed-in account and is the only caller — never expose this through a controller of its own. Every call takes the parent
 * and only ever touches that parent's own customers. "Remove" deactivates; nothing is deleted.
 */
@Service
@RequiredArgsConstructor
public class CustomerPortalSubCustomers {

    private static final String ENTITY = "Customer";
    private static final Pattern PHONE = Pattern.compile("^[+]?[0-9 \\-]{7,20}$");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    static final int MAX_SUB_CUSTOMERS = 500;
    private static final int CODE_ATTEMPTS = 5;

    private final CustomerRepository repository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<Customer> list(UUID companyId, UUID parentId) {
        return repository.findChildren(companyId, parentId);
    }

    /** One of the parent's own customers; anyone else's (or a staff-created one) is "not found". */
    @Transactional(readOnly = true)
    public Customer get(UUID companyId, UUID parentId, UUID id) {
        return owned(companyId, parentId, id);
    }

    @Transactional
    public Customer create(UUID companyId, UUID parentId, SubCustomerCommand c) {
        if (repository.findChildren(companyId, parentId).size() >= MAX_SUB_CUSTOMERS) {
            throw new BusinessRuleException("You can register at most " + MAX_SUB_CUSTOMERS + " customers.");
        }
        validate(c);
        String mobile = c.mobile().trim();
        if (repository.findByCompanyIdAndMobile(companyId, mobile).isPresent()) {
            throw new BusinessRuleException("A customer with this mobile number already exists.");
        }
        Customer customer = Customer.builder()
                .customerCode(nextCode(companyId))
                .customerType(CustomerType.INDIVIDUAL)
                .parentCustomerId(parentId)
                .status(CustomerStatus.ACTIVE)
                .build();
        apply(customer, c);
        customer.applyInvariants();
        Customer saved = repository.save(customer);
        auditService.record(AuditAction.CUSTOMER_CREATED, ENTITY, saved.getId(),
                Map.of("customerCode", saved.getCustomerCode(), "parentCustomerId", parentId.toString(), "source", "CUSTOMER_PORTAL"));
        return saved;
    }

    @Transactional
    public Customer update(UUID companyId, UUID parentId, UUID id, SubCustomerCommand c) {
        Customer customer = owned(companyId, parentId, id);
        if (c.expectedVersion() != null && !Objects.equals(c.expectedVersion(), customer.getVersion())) {
            throw new BusinessRuleException(ErrorCode.CONCURRENT_MODIFICATION, "This customer was changed elsewhere. Reload and try again.");
        }
        validate(c);
        String mobile = c.mobile().trim();
        repository.findByCompanyIdAndMobile(companyId, mobile)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new BusinessRuleException("A customer with this mobile number already exists.");
                });
        apply(customer, c);
        customer.applyInvariants();
        Customer saved = repository.save(customer);
        auditService.record(AuditAction.CUSTOMER_UPDATED, ENTITY, saved.getId(),
                Map.of("parentCustomerId", parentId.toString(), "source", "CUSTOMER_PORTAL"));
        return saved;
    }

    @Transactional
    public void remove(UUID companyId, UUID parentId, UUID id) {
        Customer customer = owned(companyId, parentId, id);
        if (customer.isActive()) {
            customer.deactivate();
            repository.save(customer);
            auditService.record(AuditAction.CUSTOMER_DEACTIVATED, ENTITY, id,
                    Map.of("parentCustomerId", parentId.toString(), "source", "CUSTOMER_PORTAL"));
        }
    }

    // ------------------------------------------------------------------ helpers

    private Customer owned(UUID companyId, UUID parentId, UUID id) {
        return repository.findByIdWithinCompany(id, companyId)
                .filter(c -> parentId.equals(c.getParentCustomerId()))
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, id));
    }

    private static void apply(Customer customer, SubCustomerCommand c) {
        customer.setFirstName(c.firstName().trim());
        customer.setLastName(c.lastName() == null ? "" : c.lastName().trim());
        customer.setCompanyName(blankToNull(c.companyName()));
        customer.setMobile(c.mobile().trim());
        customer.setEmail(blankToNull(c.email()));
        customer.setContactPerson(blankToNull(c.contactPerson()));
    }

    private static void validate(SubCustomerCommand c) {
        if (c.firstName() == null || c.firstName().isBlank()) {
            throw new BusinessRuleException("Enter the customer's name.");
        }
        if (c.mobile() == null || !PHONE.matcher(c.mobile().trim()).matches()) {
            throw new BusinessRuleException("Enter a valid mobile number.");
        }
        if (c.email() != null && !c.email().isBlank() && !EMAIL.matcher(c.email().trim()).matches()) {
            throw new BusinessRuleException("Enter a valid e-mail address.");
        }
    }

    private String nextCode(UUID companyId) {
        for (int i = 0; i < CODE_ATTEMPTS; i++) {
            String code = CustomerCodeGenerator.generate();
            if (!repository.isCodeTaken(companyId, code, null)) {
                return code;
            }
        }
        throw new BusinessRuleException("Could not create a customer code. Please try again.");
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public record SubCustomerCommand(String firstName, String lastName, String companyName, String mobile, String email,
                                     String contactPerson, Long expectedVersion) {
    }
}
