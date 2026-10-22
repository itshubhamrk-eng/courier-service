package com.courier.modules.customer.application;

import com.courier.modules.customer.domain.CustomerRepository;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.image.LogoImages;
import com.courier.shared.security.Roles;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** A customer's brand logo, set by staff (the customer portal only reads it, through its profile). */
@Service
@RequiredArgsConstructor
public class CustomerLogoService {

    private static final String ENTITY = "Customer";
    private static final String WRITERS = "hasAnyRole('" + Roles.COMPANY_ADMIN + "', '"
            + Roles.BRANCH_MANAGER + "', '" + Roles.OPERATOR + "')";

    private final CustomerRepository repository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public Optional<String> get(UUID customerId) {
        return Optional.ofNullable(repository.findByIdWithinCompany(customerId, companyId())
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, customerId)).getLogoDataUrl());
    }

    @Transactional
    @PreAuthorize(WRITERS)
    public void upload(UUID customerId, byte[] bytes) {
        write(customerId, LogoImages.toDataUrl(bytes), "uploaded");
    }

    @Transactional
    @PreAuthorize(WRITERS)
    public void remove(UUID customerId) {
        write(customerId, null, "removed");
    }

    private void write(UUID customerId, String dataUrl, String what) {
        if (repository.setLogo(customerId, companyId(), dataUrl) == 0) {
            throw new ResourceNotFoundException(ENTITY, customerId);
        }
        auditService.record(AuditAction.CUSTOMER_UPDATED, ENTITY, customerId, Map.of("logo", what));
    }

    private static UUID companyId() {
        return CompanyContext.getCompanyId().orElseThrow(() -> new BusinessRuleException(
                "No company is bound to this request. Customers belong to a company."));
    }
}
