package com.courier.modules.kyc.domain;

/**
 * PENDING: something is still to do (a required document missing or rejected, or e-mail/mobile not verified).
 * UNDER_REVIEW: everything is uploaded and verified, waiting for the administrator to approve the documents.
 * COMPLETE: every required document approved and e-mail and mobile verified.
 */
public enum KycStatus { PENDING, UNDER_REVIEW, COMPLETE }
