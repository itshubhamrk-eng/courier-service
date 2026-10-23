package com.courier.modules.kyc.domain;

/** A document's review by the company administrator. A new upload always starts at PENDING_REVIEW. */
public enum KycReviewStatus { PENDING_REVIEW, APPROVED, REJECTED }
