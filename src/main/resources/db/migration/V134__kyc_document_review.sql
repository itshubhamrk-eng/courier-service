-- KYC documents are now reviewed by the company administrator: each upload waits for review and is
-- approved or rejected (with a reason the uploader sees). KYC is complete once every required document
-- is approved and the e-mail and mobile are verified. Documents uploaded before this change wait for review.
ALTER TABLE kyc_documents
    ADD COLUMN review_status VARCHAR(20)  NOT NULL DEFAULT 'PENDING_REVIEW' COMMENT 'PENDING_REVIEW, APPROVED or REJECTED',
    ADD COLUMN review_note   VARCHAR(500) NULL COMMENT 'Why it was rejected (shown to the uploader)',
    ADD COLUMN reviewed_by   BINARY(16)   NULL,
    ADD COLUMN reviewed_at   TIMESTAMP(6) NULL;
