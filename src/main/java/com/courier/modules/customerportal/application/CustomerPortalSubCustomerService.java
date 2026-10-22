package com.courier.modules.customerportal.application;

import com.courier.modules.customer.application.CustomerPortalAddressBook;
import com.courier.modules.customer.application.CustomerPortalAddressBook.PortalAddressCommand;
import com.courier.modules.customer.application.CustomerPortalSubCustomers;
import com.courier.modules.customer.application.CustomerPortalSubCustomers.SubCustomerCommand;
import com.courier.modules.customer.domain.Customer;
import com.courier.modules.customerportal.application.CustomerPortalAddressService.AddressView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * The customers the signed-in customer registered, and each one's addresses. Every call first proves the customer is the
 * signed-in customer's own (a staff-created or someone else's customer is "not found"), then reuses the address book.
 */
@Service
@RequiredArgsConstructor
public class CustomerPortalSubCustomerService {

    private final CurrentCustomerResolver resolver;
    private final CustomerPortalSubCustomers subCustomers;
    private final CustomerPortalAddressBook book;
    private final CustomerPortalAddressService addressViews;

    public List<Customer> list() {
        ActingCustomer a = resolver.require();
        return subCustomers.list(a.companyId(), a.customerId());
    }

    public Customer get(UUID id) {
        ActingCustomer a = resolver.require();
        return subCustomers.get(a.companyId(), a.customerId(), id);
    }

    public Customer create(SubCustomerCommand command) {
        ActingCustomer a = resolver.require();
        return subCustomers.create(a.companyId(), a.customerId(), command);
    }

    public Customer update(UUID id, SubCustomerCommand command) {
        ActingCustomer a = resolver.require();
        return subCustomers.update(a.companyId(), a.customerId(), id, command);
    }

    public void remove(UUID id) {
        ActingCustomer a = resolver.require();
        subCustomers.remove(a.companyId(), a.customerId(), id);
    }

    public List<AddressView> addresses(UUID id) {
        ActingCustomer a = resolver.require();
        subCustomers.get(a.companyId(), a.customerId(), id);
        return addressViews.views(book.list(a.companyId(), id));
    }

    public AddressView addAddress(UUID id, PortalAddressCommand command) {
        ActingCustomer a = resolver.require();
        subCustomers.get(a.companyId(), a.customerId(), id);
        return addressViews.views(List.of(book.create(a.companyId(), id, command))).get(0);
    }

    public AddressView updateAddress(UUID id, UUID addressId, PortalAddressCommand command) {
        ActingCustomer a = resolver.require();
        subCustomers.get(a.companyId(), a.customerId(), id);
        return addressViews.views(List.of(book.update(a.companyId(), id, addressId, command))).get(0);
    }

    public void removeAddress(UUID id, UUID addressId) {
        ActingCustomer a = resolver.require();
        subCustomers.get(a.companyId(), a.customerId(), id);
        book.remove(a.companyId(), id, addressId);
    }
}
