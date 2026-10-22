package com.courier.modules.customerportal.api;

import com.courier.modules.customerportal.application.ActingCustomer;
import com.courier.modules.customerportal.application.CurrentCustomerResolver;
import com.courier.modules.kyc.api.KycEndpoints;
import com.courier.modules.kyc.api.KycEndpoints.CodeRequest;
import com.courier.modules.kyc.application.KycAccess.Target;
import com.courier.modules.kyc.application.KycService;
import com.courier.modules.kyc.application.KycView;
import com.courier.modules.kyc.domain.KycSubjectType;
import com.courier.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/** The business customer's own KYC, from the customer portal. The customer comes from the signed-in account. */
@RestController
@RequestMapping("/api/v1/customer-portal/kyc")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Customer portal")
public class CustomerPortalKycController {

    private final KycService service;
    private final CurrentCustomerResolver resolver;

    @GetMapping
    @Operation(summary = "The customer's KYC checklist")
    public ApiResponse<KycView> view() {
        return ApiResponse.success(KycEndpoints.view(service, target()));
    }

    @PostMapping(value = "/documents", consumes = "multipart/form-data")
    @Operation(summary = "Upload or replace a KYC document", description = "PDF, JPEG or PNG, at most 5 MB.")
    public ApiResponse<KycView> upload(@RequestParam("documentType") String documentType,
                                       @RequestParam("file") MultipartFile file) throws IOException {
        return ApiResponse.success(KycEndpoints.upload(service, target(), documentType, file), "Document uploaded");
    }

    @DeleteMapping("/documents/{documentId}")
    @Operation(summary = "Remove a KYC document")
    public ApiResponse<KycView> remove(@PathVariable UUID documentId) {
        return ApiResponse.success(KycEndpoints.remove(service, target(), documentId));
    }

    @GetMapping("/documents/{documentId}/file")
    @Operation(summary = "Download one of the customer's KYC documents")
    public ResponseEntity<byte[]> file(@PathVariable UUID documentId) {
        return KycEndpoints.file(service, target(), documentId);
    }

    @PostMapping("/verify/{channel}/send")
    @Operation(summary = "Send a code to the customer's e-mail or mobile on record", description = "`channel` is email or mobile.")
    public ApiResponse<KycView> send(@PathVariable String channel) {
        return ApiResponse.success(KycEndpoints.send(service, target(), channel), "Code sent");
    }

    @PostMapping("/verify/{channel}")
    @Operation(summary = "Confirm the code")
    public ApiResponse<KycView> verify(@PathVariable String channel, @RequestBody CodeRequest body) {
        return ApiResponse.success(KycEndpoints.verify(service, target(), channel, body == null ? null : body.code()), "Verified");
    }

    private Target target() {
        ActingCustomer a = resolver.require();
        return new Target(a.companyId(), KycSubjectType.CUSTOMER, a.customerId(), a.userId());
    }
}
