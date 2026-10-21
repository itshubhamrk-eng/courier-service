package com.courier.modules.customerportal.application;

import com.courier.modules.customer.application.CustomerPortalAddressBook;
import com.courier.modules.customer.application.CustomerPortalAddressBook.PortalAddress;
import com.courier.modules.customer.application.CustomerPortalAddressBook.PortalAddressCommand;
import com.courier.modules.master.application.PincodeGeography;
import com.courier.modules.master.application.PincodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** The signed-in customer's address book: list, add, edit, remove. City and district come from the pincode master. */
@Service
@RequiredArgsConstructor
public class CustomerPortalAddressService {

    private final CurrentCustomerResolver resolver;
    private final CustomerPortalAddressBook book;
    private final PincodeService pincodes;

    public List<AddressView> list() {
        ActingCustomer acting = resolver.require();
        return views(book.list(acting.companyId(), acting.customerId()));
    }

    public AddressView create(PortalAddressCommand command) {
        ActingCustomer acting = resolver.require();
        return views(List.of(book.create(acting.companyId(), acting.customerId(), command))).get(0);
    }

    public AddressView update(UUID addressId, PortalAddressCommand command) {
        ActingCustomer acting = resolver.require();
        return views(List.of(book.update(acting.companyId(), acting.customerId(), addressId, command))).get(0);
    }

    public void remove(UUID addressId) {
        ActingCustomer acting = resolver.require();
        book.remove(acting.companyId(), acting.customerId(), addressId);
    }

    /** Names looked up once per distinct pincode. */
    List<AddressView> views(List<PortalAddress> addresses) {
        Map<String, Optional<PincodeGeography>> geo = new HashMap<>();
        return addresses.stream().map(a -> {
            Optional<PincodeGeography> g = a.pincode() == null ? Optional.empty()
                    : geo.computeIfAbsent(a.pincode(), pincodes::geography);
            return new AddressView(a.id(), a.addressType(), a.addressUsage(), a.addressLine1(), a.addressLine2(),
                    a.landmark(), a.pincode(), g.map(PincodeGeography::areaName).orElse(null),
                    g.map(PincodeGeography::cityName).orElse(null), g.map(PincodeGeography::districtName).orElse(null),
                    a.defaultPickup(), a.defaultDelivery(), a.version());
        }).toList();
    }

    public record AddressView(UUID id, String addressType, String addressUsage, String addressLine1, String addressLine2,
                              String landmark, String pincode, String area, String city, String district,
                              boolean defaultPickup, boolean defaultDelivery, Long version) {
    }
}
