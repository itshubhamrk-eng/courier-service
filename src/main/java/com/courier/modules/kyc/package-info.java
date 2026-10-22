/**
 * KYC for users, branches and business customers: each signs in, uploads the documents its type needs
 * ({@link com.courier.modules.kyc.application.KycDocumentCatalog}) and verifies its e-mail and mobile with a
 * one-time code. Complete (auto-approved) once all required documents are present and both are verified.
 * Company administrators can view every profile and download its documents.
 */
package com.courier.modules.kyc;
