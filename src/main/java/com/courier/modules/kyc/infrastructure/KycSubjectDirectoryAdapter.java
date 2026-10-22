package com.courier.modules.kyc.infrastructure;

import com.courier.modules.auth.domain.UserRepository;
import com.courier.modules.company.domain.BranchRepository;
import com.courier.modules.customer.domain.CustomerRepository;
import com.courier.modules.customer.domain.CustomerType;
import com.courier.modules.kyc.application.KycSubject;
import com.courier.modules.kyc.application.KycSubjectDirectory;
import com.courier.modules.kyc.domain.KycSubjectType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** Users, branches and business customers as KYC subjects, always within the given company. */
@Component
@RequiredArgsConstructor
public class KycSubjectDirectoryAdapter implements KycSubjectDirectory {

    private final UserRepository users;
    private final BranchRepository branches;
    private final CustomerRepository customers;

    @Override
    @Transactional(readOnly = true)
    public Optional<KycSubject> find(UUID companyId, KycSubjectType type, UUID id) {
        if (companyId == null || id == null) {
            return Optional.empty();
        }
        return switch (type) {
            case USER -> users.findByIdWithinCompany(id, companyId).map(u -> new KycSubject(type, u.getId(),
                    u.displayName(), u.getEmail(), u.getMobile() != null && !u.getMobile().isBlank() ? u.getMobile() : u.getPhone()));
            case BRANCH -> branches.findByIdWithinCompany(id, companyId).map(b -> new KycSubject(type, b.getId(),
                    b.getBranchName(), b.getEmail(), b.getMobile()));
            case CUSTOMER -> customers.findByIdWithinCompany(id, companyId)
                    .filter(c -> c.getCustomerType() == CustomerType.BUSINESS)
                    .map(c -> new KycSubject(type, c.getId(), c.displayName(), c.getEmail(), c.getMobile()));
        };
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> branchOf(UUID companyId, UUID userId) {
        return users.findByIdWithinCompany(userId, companyId).map(u -> u.getBranchId());
    }
}
