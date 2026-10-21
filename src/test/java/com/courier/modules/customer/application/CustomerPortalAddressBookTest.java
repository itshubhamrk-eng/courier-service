package com.courier.modules.customer.application;

import com.courier.modules.customer.application.CustomerPortalAddressBook.PortalAddress;
import com.courier.modules.customer.application.CustomerPortalAddressBook.PortalAddressCommand;
import com.courier.modules.customer.domain.AddressUsage;
import com.courier.modules.customer.domain.CustomerAddress;
import com.courier.modules.customer.domain.CustomerAddressRepository;
import com.courier.modules.customer.domain.CustomerStatus;
import com.courier.modules.master.application.PincodeLocation;
import com.courier.modules.master.application.PincodeService;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomerPortalAddressBookTest {

    private static final UUID COMPANY = UUID.randomUUID();
    private static final UUID CUSTOMER = UUID.randomUUID();
    private static final UUID PINCODE_ID = UUID.randomUUID();

    private final CustomerAddressRepository repository = mock(CustomerAddressRepository.class);
    private final PincodeService pincodes = mock(PincodeService.class);
    private final List<CustomerAddress> rows = new ArrayList<>();
    private CustomerPortalAddressBook book;

    @BeforeEach
    void setUp() {
        book = new CustomerPortalAddressBook(repository, pincodes, mock(AuditService.class));
        when(pincodes.location(anyString())).thenReturn(Optional.empty());
        when(pincodes.location("411016")).thenReturn(Optional.of(
                new PincodeLocation(PINCODE_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), true, true, false)));
        when(pincodes.codeOf(PINCODE_ID)).thenReturn(Optional.of("411016"));
        when(repository.findAllByCustomerIdWithinCompany(CUSTOMER, COMPANY)).thenAnswer(i -> List.copyOf(rows));
        when(repository.findByIdWithinCompany(any(), any())).thenAnswer(i -> rows.stream()
                .filter(r -> r.getId().equals(i.getArgument(0))).findFirst());
        when(repository.save(any())).thenAnswer(i -> {
            CustomerAddress a = i.getArgument(0);
            if (a.getId() == null) {
                a.setId(UUID.randomUUID());
            }
            if (rows.stream().noneMatch(r -> r == a)) {
                rows.add(a);
            }
            return a;
        });
    }

    private static PortalAddressCommand command(String line, String pincode, boolean defaultPickup) {
        return new PortalAddressCommand(null, null, line, null, null, pincode, defaultPickup, false, null);
    }

    @Test
    @DisplayName("creates from a pincode on file, filling the geography ids, and shows the pincode back")
    void creates() {
        PortalAddress a = book.create(COMPANY, CUSTOMER, command("Shop 4, Thorat Chowk", "411016", true));

        assertThat(a.pincode()).isEqualTo("411016");
        assertThat(a.addressUsage()).isEqualTo("BOTH");
        assertThat(a.defaultPickup()).isTrue();
        assertThat(rows.get(0).getPincodeId()).isEqualTo(PINCODE_ID);
        assertThat(rows.get(0).getCustomerId()).isEqualTo(CUSTOMER);
    }

    @Test
    @DisplayName("a pincode not in the directory cannot be saved")
    void unknownPincode() {
        assertThatThrownBy(() -> book.create(COMPANY, CUSTOMER, command("Shop 4", "999999", false)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("not in our directory");
    }

    @Test
    @DisplayName("the same address twice is refused")
    void duplicate() {
        book.create(COMPANY, CUSTOMER, command("Shop 4, Thorat Chowk", "411016", false));

        assertThatThrownBy(() -> book.create(COMPANY, CUSTOMER, command("  shop 4,  thorat chowk ", "411016", false)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("a new default pickup address clears the old one")
    void oneDefault() {
        book.create(COMPANY, CUSTOMER, command("Shop 4", "411016", true));
        book.create(COMPANY, CUSTOMER, command("Warehouse 9", "411016", true));

        assertThat(rows).filteredOn(CustomerAddress::isDefaultPickup).extracting(CustomerAddress::getAddressLine1)
                .containsExactly("Warehouse 9");
    }

    @Test
    @DisplayName("a delivery-only address cannot be the default pickup")
    void usageMatchesDefault() {
        PortalAddressCommand c = new PortalAddressCommand(null, AddressUsage.DELIVERY, "Shop 4", null, null, "411016",
                true, false, null);

        assertThatThrownBy(() -> book.create(COMPANY, CUSTOMER, c)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("another customer's address is not found; removing deactivates and drops it from the list")
    void ownershipAndRemove() {
        PortalAddress mine = book.create(COMPANY, CUSTOMER, command("Shop 4", "411016", true));

        assertThatThrownBy(() -> book.remove(COMPANY, UUID.randomUUID(), mine.id()))
                .isInstanceOf(ResourceNotFoundException.class);

        book.remove(COMPANY, CUSTOMER, mine.id());

        assertThat(rows.get(0).getStatus()).isEqualTo(CustomerStatus.INACTIVE);
        assertThat(rows.get(0).isDefaultPickup()).isFalse();
        assertThat(book.list(COMPANY, CUSTOMER)).isEmpty();
        assertThatThrownBy(() -> book.update(COMPANY, CUSTOMER, mine.id(), command("Shop 5", "411016", false)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("a stale expectedVersion is a conflict")
    void staleVersion() {
        PortalAddress mine = book.create(COMPANY, CUSTOMER, command("Shop 4", "411016", false));
        rows.get(0).setVersion(3L);

        assertThatThrownBy(() -> book.update(COMPANY, CUSTOMER, mine.id(),
                new PortalAddressCommand(null, null, "Shop 5", null, null, "411016", false, false, 2L)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("changed elsewhere");
    }
}
