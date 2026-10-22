package com.courier.modules.kyc.application;

import com.courier.modules.kyc.domain.KycSubjectType;

import java.util.UUID;

/** Who a KYC profile is for, with the e-mail and mobile on their record today (what gets verified). */
public record KycSubject(KycSubjectType type, UUID id, String name, String email, String mobile) {
}
