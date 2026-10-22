package com.courier.modules.customerportal.integrations.application;

import com.courier.modules.customerportal.integrations.domain.CourierIntegration;
import com.courier.modules.customerportal.integrations.domain.CourierIntegrationRepository;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ResourceNotFoundException;
import com.courier.shared.security.Roles;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
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

    static final int MAX_BYTES = 150 * 1024;
    private static final String COMPANY_ADMIN = "hasRole('" + Roles.COMPANY_ADMIN + "')";

    private final CourierIntegrationRepository repository;
    private final AuditService auditService;

    @Transactional
    @PreAuthorize(COMPANY_ADMIN)
    public void upload(String code, byte[] bytes) {
        UUID companyId = CompanyContext.requireCompanyId();
        CourierCatalog.Provider provider = CourierCatalog.find(code).orElseThrow(() -> new ResourceNotFoundException("Courier", code));
        if (bytes == null || bytes.length == 0) {
            throw new BusinessRuleException("Choose a logo file.");
        }
        if (bytes.length > MAX_BYTES) {
            throw new BusinessRuleException("The logo is larger than " + (MAX_BYTES / 1024) + " KB. Use a smaller image.");
        }
        String type = sniff(bytes);
        if (type == null) {
            throw new BusinessRuleException("The logo must be a PNG, JPEG or WebP image.");
        }
        CourierIntegration row = repository.findByProvider(companyId, provider.code()).orElseGet(() -> {
            CourierIntegration created = new CourierIntegration();
            created.setProviderCode(provider.code());
            return created;
        });
        row.setLogoDataUrl("data:" + type + ";base64," + Base64.getEncoder().encodeToString(bytes));
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

    /** The image type from the file's signature, or null when it is not one of the accepted types. */
    static String sniff(byte[] b) {
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "image/png";
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        return null;
    }
}
