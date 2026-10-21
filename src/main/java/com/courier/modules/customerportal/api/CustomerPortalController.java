package com.courier.modules.customerportal.api;

import com.courier.modules.customerportal.api.CustomerPortalDtos.ProfileResponse;
import com.courier.modules.customerportal.application.CustomerPortalDashboardService;
import com.courier.modules.customerportal.application.CustomerPortalDashboardService.Dashboard;
import com.courier.modules.customerportal.application.CustomerPortalProfileService;
import com.courier.modules.customerportal.application.PincodeLookupService;
import com.courier.modules.customerportal.application.PincodeLookupService.PincodeInfo;
import com.courier.modules.customerportal.application.CustomerPortalProfileService.Profile;
import com.courier.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final CustomerPortalDashboardService dashboardService;
    private final PincodeLookupService pincodeLookupService;

    @GetMapping("/profile")
    @Operation(summary = "The signed-in customer's profile and addresses")
    public ApiResponse<ProfileResponse> profile() {
        Profile profile = profileService.profile();
        return ApiResponse.success(ProfileResponse.of(profile.customer(), profile.addresses()));
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Shipment counts by stage and the most recent shipments")
    public ApiResponse<Dashboard> dashboard() {
        return ApiResponse.success(dashboardService.dashboard());
    }

    @GetMapping("/pincodes/{pincode}")
    @Operation(summary = "City/district of a pincode and whether OCCPL's own network serves it",
            description = "422 for a malformed pincode; `known=false` when it is not in the pincode master.")
    public ApiResponse<PincodeInfo> pincode(@PathVariable String pincode) {
        return ApiResponse.success(pincodeLookupService.lookup(pincode));
    }
}
