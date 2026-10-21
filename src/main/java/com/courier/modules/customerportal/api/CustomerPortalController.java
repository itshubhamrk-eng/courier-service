package com.courier.modules.customerportal.api;

import com.courier.modules.customerportal.api.CustomerPortalDtos.ProfileResponse;
import com.courier.modules.customerportal.application.CustomerPortalProfileService;
import com.courier.modules.customerportal.application.CustomerPortalProfileService.Profile;
import com.courier.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The customer portal API. Customer accounts only; the customer comes from the signed-in account. */
@RestController
@RequestMapping("/api/v1/customer-portal")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Customer portal", description = "Business customers: profile, rates, bookings, tracking")
public class CustomerPortalController {

    private final CustomerPortalProfileService profileService;

    @GetMapping("/profile")
    @Operation(summary = "The signed-in customer's profile and addresses")
    public ApiResponse<ProfileResponse> profile() {
        Profile profile = profileService.profile();
        return ApiResponse.success(ProfileResponse.of(profile.customer(), profile.addresses()));
    }
}
