package com.courier.modules.customerportal.api;

import com.courier.modules.customerportal.api.CustomerPortalDtos.AddressRequest;
import com.courier.modules.customerportal.api.CustomerPortalDtos.ProfileResponse;
import com.courier.modules.customerportal.application.CustomerPortalAddressService;
import com.courier.modules.customerportal.application.CustomerPortalAddressService.AddressView;
import com.courier.modules.customerportal.application.CustomerPortalShipmentTracking;
import com.courier.modules.customerportal.application.CustomerPortalShipmentTracking.ShipmentTracking;
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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

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
    private final CustomerPortalAddressService addressService;
    private final CustomerPortalShipmentTracking shipmentTracking;

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

    @GetMapping("/addresses")
    @Operation(summary = "The customer's address book")
    public ApiResponse<List<AddressView>> addresses() {
        return ApiResponse.success(addressService.list());
    }

    @PostMapping("/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add an address", description = "422 when the pincode is not in the pincode master or the address exists.")
    public ApiResponse<AddressView> addAddress(@Valid @RequestBody AddressRequest request) {
        return ApiResponse.success(addressService.create(request.toCommand()));
    }

    @PutMapping("/addresses/{addressId}")
    @Operation(summary = "Edit an address", description = "Send `expectedVersion` from the list; 409 when it changed meanwhile.")
    public ApiResponse<AddressView> updateAddress(@PathVariable UUID addressId, @Valid @RequestBody AddressRequest request) {
        return ApiResponse.success(addressService.update(addressId, request.toCommand()));
    }

    @DeleteMapping("/addresses/{addressId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove an address from the address book")
    public void removeAddress(@PathVariable UUID addressId) {
        addressService.remove(addressId);
    }

    @GetMapping("/shipments/{shipmentId}/tracking")
    @Operation(summary = "Status history of a shipment booked at an OCCPL counter (dashboard's recent shipments)")
    public ApiResponse<ShipmentTracking> shipmentTracking(@PathVariable UUID shipmentId) {
        return ApiResponse.success(shipmentTracking.track(shipmentId));
    }
}
