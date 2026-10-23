package com.courier.modules.kyc.application;

import com.courier.modules.kyc.domain.KycStatus;
import com.courier.modules.kyc.domain.KycSubjectType;

import java.time.Instant;
import java.util.UUID;

/** One row of the administrator's KYC overview. */
public record KycSummary(KycSubjectType subjectType, UUID subjectId, String subjectName, KycStatus status,
                         boolean emailVerified, boolean mobileVerified, int requiredUploaded, int requiredApproved,
                         int requiredTotal, int awaitingReview,
                         Instant updatedAt, Instant completedAt) {
}
