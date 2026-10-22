package com.courier.modules.kyc.application;

import com.courier.modules.kyc.domain.KycSubjectType;

import java.util.Optional;
import java.util.UUID;

/** Looks up a user, branch or business customer of a company. */
public interface KycSubjectDirectory {

    Optional<KycSubject> find(UUID companyId, KycSubjectType type, UUID id);

    /** The branch a staff user is placed at, if any. */
    Optional<UUID> branchOf(UUID companyId, UUID userId);
}
