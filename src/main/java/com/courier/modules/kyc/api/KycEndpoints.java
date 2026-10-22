package com.courier.modules.kyc.api;

import com.courier.modules.kyc.application.KycAccess.Target;
import com.courier.modules.kyc.application.KycFile;
import com.courier.modules.kyc.application.KycService;
import com.courier.modules.kyc.application.KycView;
import com.courier.modules.kyc.domain.KycChannel;
import com.courier.shared.exception.BusinessRuleException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** The KYC operations shared by the staff and customer-portal controllers, once the target is resolved. */
public final class KycEndpoints {

    private KycEndpoints() {
    }

    public static KycView view(KycService s, Target t) {
        return s.view(t.companyId(), t.type(), t.subjectId());
    }

    public static KycView upload(KycService s, Target t, String documentType, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Choose a file to upload.");
        }
        return s.upload(t.companyId(), t.type(), t.subjectId(), documentType, file.getOriginalFilename(), file.getBytes(), t.actorId());
    }

    public static KycView remove(KycService s, Target t, UUID documentId) {
        return s.removeDocument(t.companyId(), t.type(), t.subjectId(), documentId);
    }

    public static KycView send(KycService s, Target t, String channel) {
        return s.sendCode(t.companyId(), t.type(), t.subjectId(), channel(channel));
    }

    public static KycView verify(KycService s, Target t, String channel, String code) {
        return s.verifyCode(t.companyId(), t.type(), t.subjectId(), channel(channel), code);
    }

    /** The document as a download (never rendered by the API's origin: attachment + nosniff). */
    public static ResponseEntity<byte[]> file(KycService s, Target t, UUID documentId) {
        KycFile f = s.file(t.companyId(), t.type(), t.subjectId(), documentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(f.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(f.content());
    }

    private static KycChannel channel(String raw) {
        return switch (raw == null ? "" : raw.toLowerCase()) {
            case "email" -> KycChannel.EMAIL;
            case "mobile" -> KycChannel.MOBILE;
            default -> throw new BusinessRuleException("Channel must be email or mobile.");
        };
    }

    public record CodeRequest(String code) {
    }
}
