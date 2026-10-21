package com.courier.modules.customerportal.api;

import com.courier.modules.customer.domain.Customer;
import com.courier.modules.customer.domain.CustomerAddress;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request and response shapes of the customer portal API. */
public final class CustomerPortalDtos {

    private CustomerPortalDtos() {
    }

    @Schema(name = "CustomerPortalProfile")
    public record ProfileResponse(
            UUID customerId,
            String customerCode,
            String companyName,
            String contactPerson,
            String mobile,
            String email,
            String gstNumber,
            String panNumber,
            String defaultPaymentMode,
            BillingAddress billingAddress,
            List<AddressResponse> addresses) {

        static ProfileResponse of(Customer c, List<CustomerAddress> addresses) {
            return new ProfileResponse(c.getId(), c.getCustomerCode(), c.getCompanyName(), c.getContactPerson(),
                    c.getMobile(), c.getEmail(), c.getGstNumber(), c.getPanNumber(),
                    c.getDefaultPaymentMode() == null ? null : c.getDefaultPaymentMode().name(),
                    new BillingAddress(c.getBillingAddressLine1(), c.getBillingAddressLine2(), c.getBillingCity(),
                            c.getBillingState(), c.getBillingPincode()),
                    addresses.stream().map(AddressResponse::of).toList());
        }
    }

    public record BillingAddress(String line1, String line2, String city, String state, String pincode) {
    }

    @Schema(name = "CustomerPortalAddress")
    public record AddressResponse(
            UUID id,
            String addressType,
            String addressUsage,
            String addressLine1,
            String addressLine2,
            String landmark,
            UUID stateId,
            UUID districtId,
            UUID cityId,
            UUID pincodeId,
            boolean defaultPickup,
            boolean defaultDelivery) {

        static AddressResponse of(CustomerAddress a) {
            return new AddressResponse(a.getId(),
                    a.getAddressType() == null ? null : a.getAddressType().name(),
                    a.getAddressUsage() == null ? null : a.getAddressUsage().name(),
                    a.getAddressLine1(), a.getAddressLine2(), a.getLandmark(),
                    a.getStateId(), a.getDistrictId(), a.getCityId(), a.getPincodeId(),
                    a.isDefaultPickup(), a.isDefaultDelivery());
        }
    }

    @Schema(name = "CustomerPortalAccessRequest",
            description = "All optional: email defaults to the customer's email, first name to its contact person.")
    public record GrantAccessRequest(
            @Email @Size(max = 255) String email,
            @Size(max = 100) String firstName,
            @Size(max = 100) String lastName) {
    }

    @Schema(name = "CustomerPortalAccessGranted",
            description = "temporaryPassword is shown once; the account stays PENDING until the activation link is used.")
    public record GrantAccessResponse(UUID userId, String email, String temporaryPassword, boolean activationEmailSent) {
    }

    @Schema(name = "CustomerPortalAccess")
    public record AccessResponse(boolean hasAccount, UUID userId, String email, String status, Instant lastLoginAt) {
    }
}
