package com.courier.modules.kyc.application;

import com.courier.modules.kyc.domain.KycStatus;
import com.courier.modules.kyc.domain.KycSubjectType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A subject's KYC checklist: e-mail and mobile verification and one slot per document type. */
public record KycView(KycSubjectType subjectType, UUID subjectId, String subjectName, KycStatus status,
                      String email, boolean emailVerified, String mobile, boolean mobileVerified,
                      int requiredUploaded, int requiredTotal, List<Slot> documents, Instant completedAt) {

    public record Slot(String code, String label, String hint, boolean required, Uploaded uploaded) {
    }

    public record Uploaded(UUID id, String fileName, String contentType, long sizeBytes, Instant uploadedAt) {
    }
}
