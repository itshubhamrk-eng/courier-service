package com.courier.modules.customerportal.integrations.application;

import com.courier.modules.customerportal.integrations.domain.CourierIntegration;
import com.courier.modules.customerportal.integrations.domain.CourierIntegrationRepository;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.image.LogoImages;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.security.Roles;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The logo a company shows for each courier. The company administrator uploads it while setting the courier up; the
 * customer portal reads every logo of its company in one call. The file type comes from the file's first bytes, never
 * from the upload's declared type, and only raster images are accepted (an SVG can carry script).
 */
@Service
@RequiredArgsConstructor
public class CourierLogoService {

    private static final String COMPANY_ADMIN = "hasRole('" + Roles.COMPANY_ADMIN + "')";

    private final CourierIntegrationRepository repository;
    private final AuditService auditService;

    @Transactional
    @PreAuthorize(COMPANY_ADMIN)
    public void upload(String code, byte[] bytes) {
        UUID companyId = CompanyContext.requireCompanyId();
        CourierCatalog.Provider provider = CourierCatalog.find(code).orElseThrow(() -> new ResourceNotFoundException("Courier", code));
        String dataUrl = LogoImages.toDataUrl(bytes);
        CourierIntegration row = repository.findByProvider(companyId, provider.code()).orElseGet(() -> {
            CourierIntegration created = new CourierIntegration();
            created.setProviderCode(provider.code());
            return created;
        });
        row.setLogoDataUrl(dataUrl);
        CourierIntegration saved = repository.save(row);
        auditService.record(AuditAction.COURIER_INTEGRATION_SAVED, "CourierIntegration", saved.getId(),
                Map.of("provider", provider.code(), "logo", "uploaded"));
    }

    @Transactional
    @PreAuthorize(COMPANY_ADMIN)
    public void remove(String code) {
        UUID companyId = CompanyContext.requireCompanyId();
        CourierCatalog.Provider provider = CourierCatalog.find(code).orElseThrow(() -> new ResourceNotFoundException("Courier", code));
        repository.findByProvider(companyId, provider.code()).ifPresent(row -> {
            row.setLogoDataUrl(null);
            repository.save(row);
            auditService.record(AuditAction.COURIER_INTEGRATION_SAVED, "CourierIntegration", row.getId(),
                    Map.of("provider", provider.code(), "logo", "removed"));
        });
    }

    /**
     * Every logo of the signed-in company, by courier code. No role check: the customer portal calls it, and the company
     * comes from the signed-in account like every other read there.
     */
    @Transactional(readOnly = true)
    public Map<String, String> logosOfCompany() {
        UUID companyId = CompanyContext.requireCompanyId();
        Map<String, String> logos = new LinkedHashMap<>();
        for (CourierIntegration row : repository.findAllOfCompany(companyId)) {
            if (row.getLogoDataUrl() != null) {
                logos.put(row.getProviderCode(), row.getLogoDataUrl());
            }
        }
        return logos;
    }
}
