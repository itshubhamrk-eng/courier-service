package com.courier.modules.customerportal.integrations.api;

import com.courier.modules.customerportal.integrations.application.CourierIntegrationService;
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
import org.springframework.web.bind.annotation.RestController;

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

    public record SaveRequest(boolean enabled, Map<String, String> values) {
    }
}
