package com.courier.modules.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Starts a password reset.
 *
 * <p>The endpoint always responds 200 regardless of whether the address exists —
 * see {@code PasswordService#forgotPassword}.
 */
@Schema(name = "ForgotPasswordRequest", description = "Request a password reset link")
public record ForgotPasswordRequest(

        @Schema(description = "Company to reset in. Either this or companyCode is required.")
        UUID companyId,

        @Schema(description = "Company code, the alternative to companyId (what a login form has).",
                example = "OCCPL_COURIER")
        @Size(max = 63)
        String companyCode,

        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email address")
        @Size(max = 255)
        String email
) {

    @AssertTrue(message = "companyId or companyCode is required")
    @Schema(hidden = true)
    public boolean isCompanyGiven() {
        return companyId != null || (companyCode != null && !companyCode.isBlank());
    }
}
