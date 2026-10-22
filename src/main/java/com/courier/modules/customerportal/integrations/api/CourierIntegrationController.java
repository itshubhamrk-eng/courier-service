package com.courier.modules.customerportal.integrations.api;

import com.courier.modules.customerportal.integrations.application.CourierIntegrationService;
import com.courier.modules.customerportal.integrations.application.CourierLogoService;
import com.courier.modules.customerportal.integrations.application.CourierIntegrationService.IntegrationView;
import com.courier.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import java.util.List;
import java.util.Map;

/** The company's own accounts with external couriers (COMPANY_ADMIN only). Secrets are write-only. */
@RestController
@RequestMapping("/api/v1/courier-integrations")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Courier integrations", description = "Per-company courier API accounts (COMPANY_ADMIN only)")
public class CourierIntegrationController {

    private final CourierIntegrationService service;
    private final CourierLogoService logos;

    @GetMapping
    @Operation(summary = "Every courier with its connection state and form fields",
            description = "Secret values are never returned, only whether each is set.")
    public ApiResponse<List<IntegrationView>> list() {
        return ApiResponse.success(service.list());
    }

    @PutMapping("/{code}")
    @Operation(summary = "Save (and enable or disable) the company's account with a courier",
            description = "`values` maps field keys to values. A blank or missing secret keeps the stored one. "
                    + "Enabling needs every required field. 422 for a courier that cannot be connected yet.")
    public ApiResponse<IntegrationView> save(@PathVariable String code, @RequestBody SaveRequest request) {
        return ApiResponse.success(service.save(code, request.enabled(), request.values()), "Courier integration saved");
    }

    @PostMapping("/{code}/test")
    @Operation(summary = "Check that the courier accepts the saved credentials",
            description = "Makes one real call to the courier. The outcome is in `lastTestOk` / `lastTestMessage`.")
    public ApiResponse<IntegrationView> test(@PathVariable String code) {
        return ApiResponse.success(service.test(code));
    }

    @DeleteMapping("/{code}")
    @Operation(summary = "Disconnect a courier: removes the credentials and switches it off for the company")
    public ApiResponse<IntegrationView> disconnect(@PathVariable String code) {
        return ApiResponse.success(service.disconnect(code), "Courier disconnected");
    }

    @PutMapping(value = "/{code}/logo", consumes = "multipart/form-data")
    @Operation(summary = "Upload the courier's logo shown to the company's customers",
            description = "PNG, JPEG or WebP, at most 150 KB. The type is read from the file itself.")
    public ApiResponse<Void> uploadLogo(@PathVariable String code, @RequestParam("file") MultipartFile file) throws IOException {
        logos.upload(code, file.getBytes());
        return ApiResponse.success("Logo saved");
    }

    @DeleteMapping("/{code}/logo")
    @Operation(summary = "Remove the courier's logo")
    public ApiResponse<Void> removeLogo(@PathVariable String code) {
        logos.remove(code);
        return ApiResponse.success("Logo removed");
    }

    public record SaveRequest(boolean enabled, Map<String, String> values) {
    }
}
