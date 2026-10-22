package com.courier.modules.kyc.application;

import com.courier.modules.kyc.domain.KycSubjectType;
import com.courier.shared.exception.ForbiddenException;
import com.courier.shared.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KycAccessTest {

    private static final UUID COMPANY = UUID.randomUUID();
    private final KycSubjectDirectory directory = mock(KycSubjectDirectory.class);
    private final KycAccess access = new KycAccess(directory);

    private AuthenticatedUser signIn(String... roles) {
        AuthenticatedUser u = new AuthenticatedUser(UUID.randomUUID(), COMPANY, "a@b.in", Set.of(roles), "jti");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.authorities()));
        return u;
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("a staff user's own KYC is always their own user id")
    void me() {
        AuthenticatedUser u = signIn("OPERATOR");
        assertThat(access.me()).isEqualTo(new KycAccess.Target(COMPANY, KycSubjectType.USER, u.userId(), u.userId()));
    }

    @Test
    @DisplayName("branch KYC: only a branch manager, and only for the branch they are placed at")
    void branch() {
        signIn("OPERATOR");
        assertThatThrownBy(access::myBranch).isInstanceOf(ForbiddenException.class);
        signIn("BRANCH_MANAGER");
        when(directory.branchOf(any(), any())).thenReturn(Optional.empty());
        assertThatThrownBy(access::myBranch).isInstanceOf(ForbiddenException.class).hasMessageContaining("not placed");
        UUID branch = UUID.randomUUID();
        when(directory.branchOf(any(), any())).thenReturn(Optional.of(branch));
        assertThat(access.myBranch().subjectId()).isEqualTo(branch);
    }

    @Test
    @DisplayName("a customer-portal account cannot use staff KYC; only a company admin views others'")
    void roles() {
        signIn("CUSTOMER");
        assertThatThrownBy(access::me).isInstanceOf(ForbiddenException.class);
        signIn("BRANCH_MANAGER");
        assertThatThrownBy(() -> access.asAdmin(KycSubjectType.USER, UUID.randomUUID())).isInstanceOf(ForbiddenException.class);
        signIn("COMPANY_ADMIN");
        assertThat(access.asAdmin(KycSubjectType.CUSTOMER, null).companyId()).isEqualTo(COMPANY);
    }
}
