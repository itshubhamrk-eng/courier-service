package com.courier.modules.kyc.application;

import com.courier.modules.kyc.domain.KycReviewStatus;
import com.courier.modules.kyc.domain.KycStatus;
import com.courier.modules.kyc.domain.KycSubjectType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A subject's KYC checklist: e-mail and mobile verification and one slot per document type.
 * {@code requiredUploaded} counts required documents uploaded and not rejected; {@code requiredApproved} those approved.
 */
public record KycView(KycSubjectType subjectType, UUID subjectId, String subjectName, KycStatus status,
                      String email, boolean emailVerified, String mobile, boolean mobileVerified,
                      int requiredUploaded, int requiredApproved, int requiredTotal, List<Slot> documents, Instant completedAt) {

    public record Slot(String code, String label, String hint, boolean required, Uploaded uploaded) {
    }

    /** {@code reviewStatus} PENDING_REVIEW / APPROVED / REJECTED; {@code reviewNote} is the rejection reason. */
    public record Uploaded(UUID id, String fileName, String contentType, long sizeBytes, Instant uploadedAt,
                           KycReviewStatus reviewStatus, String reviewNote, Instant reviewedAt) {
    }
}
