package com.courier.modules.kyc.application;

import com.courier.modules.kyc.domain.KycSubjectType;

import java.util.List;
import java.util.Optional;

/** Which documents each kind of subject uploads. Codes are stored; labels and hints are for screens. */
public final class KycDocumentCatalog {

    public record DocumentSpec(String code, String label, String hint, boolean required) {
    }

    private static final List<DocumentSpec> USER = List.of(
            new DocumentSpec("ID_PROOF", "Identity proof", "Aadhaar, PAN, driving licence or voter ID", true),
            new DocumentSpec("ADDRESS_PROOF", "Address proof", "Aadhaar, utility bill, passport or rent agreement", true),
            new DocumentSpec("PHOTO", "Photograph", "A recent passport-size photo", true));

    private static final List<DocumentSpec> BRANCH = List.of(
            new DocumentSpec("BUSINESS_REGISTRATION", "Business registration / shop licence", "Shop & establishment licence, Udyam or partnership deed", true),
            new DocumentSpec("OWNER_PAN", "Owner PAN card", "PAN card of the branch owner or partner", true),
            new DocumentSpec("GST_CERTIFICATE", "GST certificate", "If the branch is GST-registered", false),
            new DocumentSpec("PREMISES_ADDRESS_PROOF", "Premises address proof", "Rent agreement, electricity bill or property tax receipt", true),
            new DocumentSpec("CANCELLED_CHEQUE", "Cancelled cheque", "For the account settlements are paid into", true),
            new DocumentSpec("SIGNED_AGREEMENT", "Signed agreement", "The branch agreement, signed", true));

    private static final List<DocumentSpec> CUSTOMER = List.of(
            new DocumentSpec("GST_CERTIFICATE", "GST certificate", "GST registration certificate", true),
            new DocumentSpec("COMPANY_PAN", "Company PAN card", "PAN card in the business's name", true),
            new DocumentSpec("BUSINESS_REGISTRATION", "Business registration", "Udyam, certificate of incorporation (CIN) or shop act licence", true),
            new DocumentSpec("SIGNATORY_ID", "Authorised signatory identity proof", "Aadhaar, PAN, driving licence or passport", true),
            new DocumentSpec("SIGNATORY_ADDRESS_PROOF", "Authorised signatory address proof", "Aadhaar, utility bill or passport", true),
            new DocumentSpec("CANCELLED_CHEQUE", "Cancelled cheque", "Of the business's bank account", true));

    private KycDocumentCatalog() {
    }

    public static List<DocumentSpec> of(KycSubjectType type) {
        return switch (type) {
            case USER -> USER;
            case BRANCH -> BRANCH;
            case CUSTOMER -> CUSTOMER;
        };
    }

    public static Optional<DocumentSpec> find(KycSubjectType type, String code) {
        return of(type).stream().filter(d -> d.code().equals(code)).findFirst();
    }
}
