package com.courier.modules.customerportal.application;

import com.courier.modules.customer.application.CustomerPortalLookup;
import com.courier.modules.customer.domain.Customer;
import com.courier.modules.customer.domain.CustomerAddress;
import com.courier.shared.exception.ErrorCode;
import com.courier.shared.exception.ForbiddenException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** The signed-in customer's own profile and addresses. */
@Service
@RequiredArgsConstructor
public class CustomerPortalProfileService {

    private final CurrentCustomerResolver resolver;
    private final CustomerPortalLookup customers;

    @Transactional(readOnly = true)
    public Profile profile() {
        ActingCustomer acting = resolver.require();
        Customer customer = customers.findActiveBusinessCustomer(acting.companyId(), acting.customerId())
                .orElseThrow(() -> new ForbiddenException(ErrorCode.ACCESS_DENIED, CurrentCustomerResolver.NO_ACCESS));
        return new Profile(customer, customers.activeAddresses(acting.companyId(), acting.customerId()));
    }

    public record Profile(Customer customer, List<CustomerAddress> addresses) {
    }
}
