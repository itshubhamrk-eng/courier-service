package com.courier.modules.kyc.application;

import com.courier.modules.kyc.domain.KycSubjectType;
import com.courier.shared.exception.ErrorCode;
import com.courier.shared.exception.ForbiddenException;
import com.courier.shared.security.AuthenticatedUser;
import com.courier.shared.security.Roles;
import com.courier.shared.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Who may act on which KYC. A staff user does their own; a branch manager does their branch's; a company
 * administrator views anyone's (read-only). Business customers do theirs from the customer portal, resolved there.
 */
@Component
@RequiredArgsConstructor
public class KycAccess {

    private final KycSubjectDirectory directory;

    public record Target(UUID companyId, KycSubjectType type, UUID subjectId, UUID actorId) {
    }

    /** The signed-in staff user's own KYC. */
    public Target me() {
        AuthenticatedUser u = staff();
        return new Target(u.companyId(), KycSubjectType.USER, u.userId(), u.userId());
    }

    /** The branch the signed-in branch manager is placed at. */
    public Target myBranch() {
        AuthenticatedUser u = staff();
        if (!u.hasRole(Roles.BRANCH_MANAGER)) {
            throw new ForbiddenException(ErrorCode.ACCESS_DENIED, "Only a branch manager can complete the branch's KYC.");
        }
        UUID branchId = directory.branchOf(u.companyId(), u.userId())
                .orElseThrow(() -> new ForbiddenException(ErrorCode.ACCESS_DENIED, "Your account is not placed at a branch."));
        return new Target(u.companyId(), KycSubjectType.BRANCH, branchId, u.userId());
    }

    /** Any subject of the administrator's own company (viewing only). */
    public Target asAdmin(KycSubjectType type, UUID subjectId) {
        AuthenticatedUser u = SecurityUtils.requireCurrentUser();
        if (!u.hasRole(Roles.COMPANY_ADMIN) || u.companyId() == null) {
            throw new ForbiddenException(ErrorCode.ACCESS_DENIED, "Only a company administrator can view others' KYC.");
        }
        return new Target(u.companyId(), type, subjectId, u.userId());
    }

    private static AuthenticatedUser staff() {
        AuthenticatedUser u = SecurityUtils.requireCurrentUser();
        if (u.companyId() == null || u.hasRole(Roles.CUSTOMER)) {
            throw new ForbiddenException(ErrorCode.ACCESS_DENIED, "KYC is for users of a company.");
        }
        return u;
    }
}
