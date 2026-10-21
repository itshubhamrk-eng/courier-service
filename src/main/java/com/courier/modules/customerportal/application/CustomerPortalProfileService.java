package com.courier.modules.customerportal.application;

import com.courier.modules.customer.application.CustomerPortalLookup;
import com.courier.modules.customer.domain.Customer;
import com.courier.shared.exception.ErrorCode;
import com.courier.shared.exception.ForbiddenException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** The signed-in customer's own profile and addresses. */
@Service
@RequiredArgsConstructor
public class CustomerPortalProfileService {

    private final CurrentCustomerResolver resolver;
    private final CustomerPortalLookup customers;
    private final CustomerPortalAddressService addresses;

    public Profile profile() {
        ActingCustomer acting = resolver.require();
        Customer customer = customers.findActiveBusinessCustomer(acting.companyId(), acting.customerId())
                .orElseThrow(() -> new ForbiddenException(ErrorCode.ACCESS_DENIED, CurrentCustomerResolver.NO_ACCESS));
        return new Profile(customer, addresses.list());
    }

    public record Profile(Customer customer, List<CustomerPortalAddressService.AddressView> addresses) {
    }
}
