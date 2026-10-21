package com.courier.modules.customerportal.api;

import com.courier.modules.auth.application.CustomerPortalAccountService.CustomerPortalAccount;
import com.courier.modules.auth.application.UserProvisioningService.ProvisionedUser;
import com.courier.modules.customerportal.api.CustomerPortalDtos.AccessResponse;
import com.courier.modules.customerportal.api.CustomerPortalDtos.GrantAccessRequest;
import com.courier.modules.customerportal.api.CustomerPortalDtos.GrantAccessResponse;
import com.courier.modules.customerportal.application.CustomerPortalAccessService;
import com.courier.modules.customerportal.application.CustomerPortalAccessService.GrantCommand;
import com.courier.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Company admins give business customers access to the customer portal. */
@RestController
@RequestMapping("/api/v1/customers/{customerId}/portal-access")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Customers", description = "Customer master data and their addresses")
public class CustomerPortalAccessController {

    private final CustomerPortalAccessService accessService;

    @PostMapping
    @Operation(summary = "Create the customer's portal account",
            description = "`COMPANY_ADMIN`. Active business customers only. The account is PENDING until the "
                    + "activation link (emailed, pointing at the customer portal) is followed. The temporary "
                    + "password is returned once.")
    public ResponseEntity<ApiResponse<GrantAccessResponse>> grant(@PathVariable UUID customerId,
                                                                  @Valid @RequestBody(required = false) GrantAccessRequest request) {
        GrantAccessRequest r = request == null ? new GrantAccessRequest(null, null, null) : request;
        ProvisionedUser user = accessService.grant(customerId, new GrantCommand(r.email(), r.firstName(), r.lastName()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(new GrantAccessResponse(
                user.userId(), user.email(), user.temporaryPassword(), user.activationEmailSent()),
                "Customer portal account created"));
    }

    @GetMapping
    @Operation(summary = "Whether the customer has a portal account", description = "`COMPANY_ADMIN`.")
    public ApiResponse<AccessResponse> get(@PathVariable UUID customerId) {
        return ApiResponse.success(accessService.find(customerId)
                .map(CustomerPortalAccessController::view)
                .orElse(new AccessResponse(false, null, null, null, null)));
    }

    @DeleteMapping
    @Operation(summary = "Disable the customer's portal account and end its sessions", description = "`COMPANY_ADMIN`.")
    public ApiResponse<AccessResponse> revoke(@PathVariable UUID customerId) {
        return ApiResponse.success(view(accessService.revoke(customerId)), "Customer portal access disabled");
    }

    private static AccessResponse view(CustomerPortalAccount a) {
        return new AccessResponse(true, a.userId(), a.email(), a.status(), a.lastLoginAt());
    }
}
