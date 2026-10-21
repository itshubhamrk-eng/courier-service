package com.courier.modules.customerportal.application;

import java.util.UUID;

/** Who a customer-portal request acts for, resolved from the signed-in account. */
public record ActingCustomer(UUID companyId, UUID userId, UUID customerId) {
}
