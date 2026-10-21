package com.courier.modules.customer.application;

import com.courier.modules.customer.domain.AddressType;
import com.courier.modules.customer.domain.AddressUsage;
import com.courier.modules.customer.domain.CustomerAddress;
import com.courier.modules.customer.domain.CustomerAddressRepository;
import com.courier.modules.customer.domain.CustomerStatus;
import com.courier.modules.master.application.PincodeLocation;
import com.courier.modules.master.application.PincodeService;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ErrorCode;
import com.courier.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A business customer's own address book, written from the customer portal ({@code customerportal}).
 *
 * <p>No {@code @PreAuthorize}, like {@link CustomerPortalLookup}: the portal resolves company and customer
 * from the signed-in account and is the only caller — never expose this through a controller of its own.
 * Staff keep using {@link CustomerAddressService}. The customer enters a pincode; the geography ids are
 * filled from the pincode master, so only pincodes on file can be saved (the address table keeps ids,
 * not a free-text pincode). "Remove" deactivates the address rather than deleting it.
 */
@Service
@RequiredArgsConstructor
public class CustomerPortalAddressBook {

    private static final String ENTITY = "CustomerAddress";
    private static final Pattern PINCODE = Pattern.compile("^[1-9][0-9]{5}$");
    static final int MAX_ADDRESSES = 50;

    private final CustomerAddressRepository repository;
    private final PincodeService pincodes;
    private final AuditService auditService;

    /** The customer's active addresses, oldest first, with each pincode's code. */
    @Transactional(readOnly = true)
    public List<PortalAddress> list(UUID companyId, UUID customerId) {
        return active(companyId, customerId).stream().map(this::view).toList();
    }

    @Transactional
    public PortalAddress create(UUID companyId, UUID customerId, PortalAddressCommand c) {
        List<CustomerAddress> existing = active(companyId, customerId);
        if (existing.size() >= MAX_ADDRESSES) {
            throw new BusinessRuleException("An address book holds at most " + MAX_ADDRESSES + " addresses.");
        }
        CustomerAddress a = CustomerAddress.builder().customerId(customerId).status(CustomerStatus.ACTIVE).build();
        apply(a, c);
        requireNotDuplicate(existing, a, null);
        clearOtherDefaults(existing, null, a);
        CustomerAddress saved = repository.save(a);
        auditService.record(AuditAction.CUSTOMER_ADDRESS_CREATED, ENTITY, saved.getId(),
                Map.of("customerId", customerId.toString(), "source", "CUSTOMER_PORTAL"));
        return view(saved);
    }

    @Transactional
    public PortalAddress update(UUID companyId, UUID customerId, UUID addressId, PortalAddressCommand c) {
        CustomerAddress a = owned(companyId, customerId, addressId);
        if (c.expectedVersion() != null && !Objects.equals(c.expectedVersion(), a.getVersion())) {
            throw new BusinessRuleException(ErrorCode.CONCURRENT_MODIFICATION, "This address was changed elsewhere. Reload and try again.");
        }
        apply(a, c);
        List<CustomerAddress> existing = active(companyId, customerId);
        requireNotDuplicate(existing, a, a.getId());
        clearOtherDefaults(existing, a.getId(), a);
        CustomerAddress saved = repository.save(a);
        auditService.record(AuditAction.CUSTOMER_ADDRESS_UPDATED, ENTITY, saved.getId(),
                Map.of("customerId", customerId.toString(), "source", "CUSTOMER_PORTAL"));
        return view(saved);
    }

    @Transactional
    public void remove(UUID companyId, UUID customerId, UUID addressId) {
        CustomerAddress a = owned(companyId, customerId, addressId);
        a.setStatus(CustomerStatus.INACTIVE);
        a.setDefaultPickup(false);
        a.setDefaultDelivery(false);
        repository.save(a);
        auditService.record(AuditAction.CUSTOMER_ADDRESS_UPDATED, ENTITY, a.getId(),
                Map.of("customerId", customerId.toString(), "source", "CUSTOMER_PORTAL", "status", "INACTIVE"));
    }

    // ------------------------------------------------------------------ helpers

    private void apply(CustomerAddress a, PortalAddressCommand c) {
        String code = c.pincode() == null ? "" : c.pincode().trim();
        if (!PINCODE.matcher(code).matches()) {
            throw new BusinessRuleException("A pincode is 6 digits and does not start with 0.");
        }
        if (c.addressLine1() == null || c.addressLine1().isBlank()) {
            throw new BusinessRuleException("Enter the address.");
        }
        PincodeLocation at = pincodes.location(code).orElseThrow(() -> new BusinessRuleException(
                "Pincode " + code + " is not in our directory, so it cannot be saved. You can still enter it when booking."));
        a.setAddressType(c.addressType() == null ? AddressType.OFFICE : c.addressType());
        a.setAddressUsage(c.addressUsage() == null ? AddressUsage.BOTH : c.addressUsage());
        a.setAddressLine1(c.addressLine1());
        a.setAddressLine2(c.addressLine2());
        a.setLandmark(c.landmark());
        a.setPincodeId(at.pincodeId());
        a.setAreaId(at.areaId());
        a.setCityId(at.cityId());
        a.setDistrictId(at.districtId());
        boolean pickup = Boolean.TRUE.equals(c.defaultPickup());
        boolean delivery = Boolean.TRUE.equals(c.defaultDelivery());
        if (pickup && a.getAddressUsage() == AddressUsage.DELIVERY) {
            throw new BusinessRuleException("A delivery-only address cannot be the default pickup address.");
        }
        if (delivery && a.getAddressUsage() == AddressUsage.PICKUP) {
            throw new BusinessRuleException("A pickup-only address cannot be the default delivery address.");
        }
        a.setDefaultPickup(pickup);
        a.setDefaultDelivery(delivery);
        a.applyInvariants();
    }

    private CustomerAddress owned(UUID companyId, UUID customerId, UUID addressId) {
        return repository.findByIdWithinCompany(addressId, companyId)
                .filter(a -> customerId.equals(a.getCustomerId()) && a.isActive())
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY, addressId));
    }

    private List<CustomerAddress> active(UUID companyId, UUID customerId) {
        return repository.findAllByCustomerIdWithinCompany(customerId, companyId).stream()
                .filter(CustomerAddress::isActive)
                .toList();
    }

    private static void requireNotDuplicate(List<CustomerAddress> existing, CustomerAddress candidate, UUID excludeId) {
        String key = candidate.duplicateKey();
        if (existing.stream().anyMatch(e -> !e.getId().equals(excludeId) && e.duplicateKey().equals(key))) {
            throw new BusinessRuleException("This address is already in your address book.");
        }
    }

    /** One default pickup and one default delivery address: setting one clears it on the others. */
    private void clearOtherDefaults(List<CustomerAddress> existing, UUID excludeId, CustomerAddress chosen) {
        for (CustomerAddress other : existing) {
            if (other.getId().equals(excludeId)) {
                continue;
            }
            boolean changed = false;
            if (chosen.isDefaultPickup() && other.isDefaultPickup()) {
                other.setDefaultPickup(false);
                changed = true;
            }
            if (chosen.isDefaultDelivery() && other.isDefaultDelivery()) {
                other.setDefaultDelivery(false);
                changed = true;
            }
            if (changed) {
                repository.save(other);
            }
        }
    }

    private PortalAddress view(CustomerAddress a) {
        Optional<String> code = pincodes.codeOf(a.getPincodeId());
        return new PortalAddress(a.getId(), a.getAddressType() == null ? null : a.getAddressType().name(),
                a.getAddressUsage() == null ? null : a.getAddressUsage().name(), a.getAddressLine1(), a.getAddressLine2(),
                a.getLandmark(), code.orElse(null), a.isDefaultPickup(), a.isDefaultDelivery(), a.getVersion());
    }

    public record PortalAddressCommand(AddressType addressType, AddressUsage addressUsage, String addressLine1,
                                       String addressLine2, String landmark, String pincode, Boolean defaultPickup,
                                       Boolean defaultDelivery, Long expectedVersion) {
    }

    /** City and district names are added by the portal from the pincode. */
    public record PortalAddress(UUID id, String addressType, String addressUsage, String addressLine1,
                                String addressLine2, String landmark, String pincode, boolean defaultPickup,
                                boolean defaultDelivery, Long version) {
    }
}
