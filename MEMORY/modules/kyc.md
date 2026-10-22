# KYC module

Users, branches and business customers complete their own KYC after signing in.

- **Who does what**: a staff user does their own (`/api/v1/kyc/me`); a branch manager does the KYC of the
  branch they are placed at (`/api/v1/kyc/branch`); a business customer does theirs from the customer portal
  (`/api/v1/customer-portal/kyc`). A company administrator views everyone's, read-only, and downloads documents
  (`/api/v1/kyc/subjects?type=`, `/subjects/{type}/{id}`, `/subjects/{type}/{id}/documents/{docId}/file`).
- **Documents** per kind: `KycDocumentCatalog` (USER: ID, address proof, photo; BRANCH: registration, owner PAN,
  GST (optional), premises proof, cancelled cheque, signed agreement; CUSTOMER: GST, company PAN, registration,
  signatory ID + address proof, cancelled cheque). One current file per type; uploading again replaces it.
  PDF/JPEG/PNG by magic bytes, ≤ 5 MB, stored in `kyc_document_files` (LONGBLOB), downloaded as attachment + nosniff.
- **E-mail / mobile**: a 6-digit code to the address/number on the subject's record (never one the caller types),
  BCrypt-hashed, 10 min, 5 tries, ≥ 60 s between sends, ≤ 5 an hour. E-mail through the deployment's SMTP provider;
  SMS through the company's SMS channel (Communication settings) — without it, mobile codes are refused with a
  clear message. `KYC_LOG_CODES=true` also logs codes (dev servers only).
- **Status**: auto-approved — COMPLETE once all required documents are uploaded and the subject's *current* e-mail
  and mobile are verified; changing either on the record or removing a required document makes it PENDING again.
  Nothing is blocked while pending (yet).
- **Real e-mail**: `COMMUNICATION_EMAIL_ENABLED=true` + `SPRING_MAIL_*` now also sends account e-mails (activation,
  password reset, verification) via `EmailNotificationSender`; otherwise the dev log-only sender stays.
