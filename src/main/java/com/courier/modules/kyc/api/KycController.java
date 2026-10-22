package com.courier.modules.kyc.api;

import com.courier.modules.kyc.api.KycEndpoints.CodeRequest;
import com.courier.modules.kyc.application.KycAccess;
import com.courier.modules.kyc.application.KycAccess.Target;
import com.courier.modules.kyc.application.KycService;
import com.courier.modules.kyc.application.KycSummary;
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
import java.util.List;
import java.util.UUID;

/**
 * Staff KYC. {@code me} is the signed-in user's own KYC; {@code branch} is the branch of the signed-in branch
 * manager. {@code subjects} is the company administrator's read-only view of everyone's KYC and documents.
 */
@RestController
@RequestMapping("/api/v1/kyc")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "KYC", description = "User and branch KYC; company administrator overview")
public class KycController {

    private final KycService service;
    private final KycAccess access;

    @GetMapping("/{scope:me|branch}")
    @Operation(summary = "KYC checklist: documents to upload and e-mail / mobile verification")
    public ApiResponse<KycView> view(@PathVariable String scope) {
        return ApiResponse.success(KycEndpoints.view(service, target(scope)));
    }

    @PostMapping(value = "/{scope:me|branch}/documents", consumes = "multipart/form-data")
    @Operation(summary = "Upload or replace a document", description = "PDF, JPEG or PNG, at most 5 MB.")
    public ApiResponse<KycView> upload(@PathVariable String scope, @RequestParam("documentType") String documentType,
                                       @RequestParam("file") MultipartFile file) throws IOException {
        return ApiResponse.success(KycEndpoints.upload(service, target(scope), documentType, file), "Document uploaded");
    }

    @DeleteMapping("/{scope:me|branch}/documents/{documentId}")
    @Operation(summary = "Remove a document")
    public ApiResponse<KycView> remove(@PathVariable String scope, @PathVariable UUID documentId) {
        return ApiResponse.success(KycEndpoints.remove(service, target(scope), documentId));
    }

    @GetMapping("/{scope:me|branch}/documents/{documentId}/file")
    @Operation(summary = "Download an uploaded document")
    public ResponseEntity<byte[]> file(@PathVariable String scope, @PathVariable UUID documentId) {
        return KycEndpoints.file(service, target(scope), documentId);
    }

    @PostMapping("/{scope:me|branch}/verify/{channel}/send")
    @Operation(summary = "Send a one-time code to the e-mail or mobile on record", description = "`channel` is email or mobile.")
    public ApiResponse<KycView> send(@PathVariable String scope, @PathVariable String channel) {
        return ApiResponse.success(KycEndpoints.send(service, target(scope), channel), "Code sent");
    }

    @PostMapping("/{scope:me|branch}/verify/{channel}")
    @Operation(summary = "Confirm the one-time code")
    public ApiResponse<KycView> verify(@PathVariable String scope, @PathVariable String channel, @RequestBody CodeRequest body) {
        return ApiResponse.success(KycEndpoints.verify(service, target(scope), channel, body == null ? null : body.code()), "Verified");
    }

    // ---------------------------------------------------------------- company administrator

    @GetMapping("/subjects")
    @Operation(summary = "KYC overview of one kind (USER, BRANCH, CUSTOMER) — company administrator")
    public ApiResponse<List<KycSummary>> list(@RequestParam KycSubjectType type) {
        Target t = access.asAdmin(type, null);
        return ApiResponse.success(service.list(t.companyId(), type));
    }

    @GetMapping("/subjects/{type}/{subjectId}")
    @Operation(summary = "One subject's KYC with its documents — company administrator")
    public ApiResponse<KycView> subject(@PathVariable KycSubjectType type, @PathVariable UUID subjectId) {
        return ApiResponse.success(KycEndpoints.view(service, access.asAdmin(type, subjectId)));
    }

    @GetMapping("/subjects/{type}/{subjectId}/documents/{documentId}/file")
    @Operation(summary = "Download one of a subject's documents — company administrator")
    public ResponseEntity<byte[]> subjectFile(@PathVariable KycSubjectType type, @PathVariable UUID subjectId,
                                              @PathVariable UUID documentId) {
        return KycEndpoints.file(service, access.asAdmin(type, subjectId), documentId);
    }

    private Target target(String scope) {
        return "branch".equals(scope) ? access.myBranch() : access.me();
    }
}
