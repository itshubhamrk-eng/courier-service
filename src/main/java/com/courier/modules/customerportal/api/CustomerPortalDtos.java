package com.courier.modules.customerportal.api;

import com.courier.modules.customer.domain.Customer;
import com.courier.modules.customer.application.CustomerPortalAddressBook;
import com.courier.modules.customer.domain.AddressType;
import com.courier.modules.customer.domain.AddressUsage;
import com.courier.modules.customerportal.application.CustomerPortalAddressService.AddressView;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
            String website,
            @Schema(description = "The customer's brand logo as a data URL, or null") String logoDataUrl,
            String defaultPaymentMode,
            BillingAddress billingAddress,
            List<AddressView> addresses) {

        static ProfileResponse of(Customer c, List<AddressView> addresses) {
            return new ProfileResponse(c.getId(), c.getCustomerCode(), c.getCompanyName(), c.getContactPerson(),
                    c.getMobile(), c.getEmail(), c.getGstNumber(), c.getPanNumber(),
                    c.getWebsite(), c.getLogoDataUrl(),
                    c.getDefaultPaymentMode() == null ? null : c.getDefaultPaymentMode().name(),
                    new BillingAddress(c.getBillingAddressLine1(), c.getBillingAddressLine2(), c.getBillingCity(),
                            c.getBillingState(), c.getBillingPincode()),
                    addresses);
        }
    }

    @Schema(name = "CustomerPortalSubCustomerRequest", description = "A customer the signed-in customer registers for itself.")
    public record SubCustomerRequest(
            @NotBlank @Size(max = 100) String firstName,
            @Size(max = 100) String lastName,
            @Size(max = 150) String companyName,
            @NotBlank @Pattern(regexp = "^[+]?[0-9 \\-]{7,20}$", message = "must be a valid phone number") String mobile,
            @Size(max = 255) String email,
            @Size(max = 150) String contactPerson,
            Long expectedVersion) {

        com.courier.modules.customer.application.CustomerPortalSubCustomers.SubCustomerCommand toCommand() {
            return new com.courier.modules.customer.application.CustomerPortalSubCustomers.SubCustomerCommand(
                    firstName, lastName, companyName, mobile, email, contactPerson, expectedVersion);
        }
    }

    @Schema(name = "CustomerPortalSubCustomer")
    public record SubCustomerView(UUID id, String customerCode, String displayName, String firstName, String lastName,
                                  String companyName, String mobile, String email, String contactPerson, String status,
                                  Long version) {

        static SubCustomerView of(Customer c) {
            return new SubCustomerView(c.getId(), c.getCustomerCode(), c.displayName(), c.getFirstName(), c.getLastName(),
                    c.getCompanyName(), c.getMobile(), c.getEmail(), c.getContactPerson(),
                    c.getStatus() == null ? null : c.getStatus().name(), c.getVersion());
        }
    }

    public record BillingAddress(String line1, String line2, String city, String state, String pincode) {
    }

    @Schema(name = "CustomerPortalAddressRequest",
            description = "The pincode must be in the pincode master; city and district are filled from it.")
    public record AddressRequest(
            AddressType addressType,
            AddressUsage addressUsage,
            @NotBlank @Size(max = 255) String addressLine1,
            @Size(max = 255) String addressLine2,
            @Size(max = 150) String landmark,
            @NotBlank @Pattern(regexp = "^[1-9][0-9]{5}$", message = "A pincode is 6 digits and does not start with 0.") String pincode,
            Boolean defaultPickup,
            Boolean defaultDelivery,
            Long expectedVersion) {

        public CustomerPortalAddressBook.PortalAddressCommand toCommand() {
            return new CustomerPortalAddressBook.PortalAddressCommand(addressType, addressUsage, addressLine1, addressLine2,
                    landmark, pincode, defaultPickup, defaultDelivery, expectedVersion);
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
