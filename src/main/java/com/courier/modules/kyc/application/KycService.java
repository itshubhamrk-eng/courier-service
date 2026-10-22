package com.courier.modules.kyc.application;

import com.courier.modules.kyc.application.KycDocumentCatalog.DocumentSpec;
import com.courier.modules.kyc.domain.KycChannel;
import com.courier.modules.kyc.domain.KycDocument;
import com.courier.modules.kyc.domain.KycDocumentFile;
import com.courier.modules.kyc.domain.KycDocumentFileRepository;
import com.courier.modules.kyc.domain.KycDocumentRepository;
import com.courier.modules.kyc.domain.KycOtp;
import com.courier.modules.kyc.domain.KycOtpRepository;
import com.courier.modules.kyc.domain.KycProfile;
import com.courier.modules.kyc.domain.KycProfileRepository;
import com.courier.modules.kyc.domain.KycStatus;
import com.courier.modules.kyc.domain.KycSubjectType;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.audit.domain.AuditAction;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * KYC of one subject (user, branch or business customer). Every method takes the subject explicitly; who may act
 * for which subject is decided by {@code KycAccess} before calling here. A profile is created on first visit.
 *
 * <p>Complete (auto-approved) when every required document is uploaded and the subject's current e-mail and mobile
 * are both verified; changing either on the subject's record, or removing a required document, makes it pending
 * again. One-time codes: 6 digits, BCrypt-hashed, valid 10 minutes, 5 tries, at most one a minute and five an hour.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KycService {

    static final Duration CODE_TTL = Duration.ofMinutes(10);
    static final int MAX_ATTEMPTS = 5;
    static final Duration RESEND_GAP = Duration.ofSeconds(60);
    static final int MAX_SENDS_PER_HOUR = 5;
    private static final String ENTITY = "KycProfile";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final KycProfileRepository profiles;
    private final KycDocumentRepository documents;
    private final KycDocumentFileRepository files;
    private final KycOtpRepository otps;
    private final KycSubjectDirectory directory;
    private final KycMessenger messenger;
    private final PasswordEncoder encoder;
    private final AuditService auditService;

    /** Writes each code to the log as well — development servers without e-mail/SMS only. */
    @Value("${app.kyc.log-codes:false}")
    private boolean logCodes;

    // ------------------------------------------------------------------ reading

    @Transactional
    public KycView view(UUID companyId, KycSubjectType type, UUID subjectId) {
        KycSubject subject = subject(companyId, type, subjectId);
        KycProfile profile = profile(companyId, subject);
        return view(subject, refresh(profile, subject));
    }

    /** Every profile of one kind in the company (administrator overview). Subjects never visited are not listed. */
    @Transactional(readOnly = true)
    public List<KycSummary> list(UUID companyId, KycSubjectType type) {
        List<KycSummary> out = new ArrayList<>();
        for (KycProfile p : profiles.findAllOfType(companyId, type)) {
            Optional<KycSubject> s = directory.find(companyId, type, p.getSubjectId());
            if (s.isEmpty()) {
                continue;
            }
            KycView v = view(s.get(), p);
            out.add(new KycSummary(type, p.getSubjectId(), s.get().name(), v.status(), v.emailVerified(), v.mobileVerified(),
                    v.requiredUploaded(), v.requiredTotal(), p.getUpdatedAt(), p.getCompletedAt()));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public KycFile file(UUID companyId, KycSubjectType type, UUID subjectId, UUID documentId) {
        KycProfile profile = profiles.findBySubject(companyId, type, subjectId)
                .orElseThrow(() -> new ResourceNotFoundException("KycDocument", documentId));
        KycDocument d = documents.findById(documentId)
                .filter(x -> x.getCompanyId().equals(companyId) && x.getProfileId().equals(profile.getId()) && !x.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("KycDocument", documentId));
        KycDocumentFile f = files.findById(d.getId()).orElseThrow(() -> new ResourceNotFoundException("KycDocument", documentId));
        return new KycFile(d.getFileName(), d.getContentType(), f.getContent());
    }

    // ------------------------------------------------------------------ documents

    /** Uploads (or replaces) the document of one type. */
    @Transactional
    public KycView upload(UUID companyId, KycSubjectType type, UUID subjectId, String documentType, String fileName,
                          byte[] content, UUID actorId) {
        KycSubject subject = subject(companyId, type, subjectId);
        DocumentSpec spec = KycDocumentCatalog.find(type, documentType)
                .orElseThrow(() -> new BusinessRuleException("Unknown document type for this KYC."));
        String contentType = KycFiles.contentTypeOf(content);
        KycProfile profile = profile(companyId, subject);
        documents.findOfType(companyId, profile.getId(), spec.code()).ifPresent(old -> {
            files.deleteById(old.getId());
            documents.delete(old);
            documents.flush();
        });
        KycDocument d = new KycDocument();
        d.setProfileId(profile.getId());
        d.setDocumentType(spec.code());
        d.setFileName(KycFiles.cleanName(fileName, contentType));
        d.setContentType(contentType);
        d.setSizeBytes(content.length);
        d.setSha256(KycFiles.sha256(content));
        d.setUploadedBy(actorId);
        KycDocument saved = documents.saveAndFlush(d);
        files.save(new KycDocumentFile(saved.getId(), content));
        auditService.record(AuditAction.KYC_UPDATED, ENTITY, profile.getId(),
                Map.of("subjectType", type.name(), "document", spec.code(), "action", "UPLOADED"));
        return view(subject, refresh(profile, subject));
    }

    @Transactional
    public KycView removeDocument(UUID companyId, KycSubjectType type, UUID subjectId, UUID documentId) {
        KycSubject subject = subject(companyId, type, subjectId);
        KycProfile profile = profile(companyId, subject);
        KycDocument d = documents.findById(documentId)
                .filter(x -> x.getCompanyId().equals(companyId) && x.getProfileId().equals(profile.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("KycDocument", documentId));
        files.deleteById(d.getId());
        documents.delete(d);
        documents.flush();
        auditService.record(AuditAction.KYC_UPDATED, ENTITY, profile.getId(),
                Map.of("subjectType", type.name(), "document", d.getDocumentType(), "action", "REMOVED"));
        return view(subject, refresh(profile, subject));
    }

    // ------------------------------------------------------------------ e-mail / mobile

    /** Sends a one-time code to the subject's current e-mail or mobile (never to an address the caller types). */
    @Transactional
    public KycView sendCode(UUID companyId, KycSubjectType type, UUID subjectId, KycChannel channel) {
        KycSubject subject = subject(companyId, type, subjectId);
        String target = target(subject, channel);
        KycProfile profile = profile(companyId, subject);
        Instant now = Instant.now();
        List<KycOtp> lastHour = otps.findRecent(companyId, profile.getId(), channel, now.minus(Duration.ofHours(1)));
        if (!lastHour.isEmpty() && lastHour.get(0).getCreatedAt() != null
                && lastHour.get(0).getCreatedAt().isAfter(now.minus(RESEND_GAP))) {
            throw new BusinessRuleException("A code was just sent. Wait a minute before asking for another.");
        }
        if (lastHour.size() >= MAX_SENDS_PER_HOUR) {
            throw new BusinessRuleException("Too many codes asked for. Try again in an hour.");
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        KycOtp otp = new KycOtp();
        otp.setProfileId(profile.getId());
        otp.setChannel(channel);
        otp.setTarget(target);
        otp.setCodeHash(encoder.encode(code));
        otp.setExpiresAt(now.plus(CODE_TTL));
        otps.save(otp);
        boolean sent = channel == KycChannel.EMAIL
                ? messenger.emailCode(companyId, target, subject.name(), code)
                : messenger.smsCode(companyId, target, code);
        if (logCodes) {
            log.info("[DEV KYC CODE] {} {} {} -> {}", type, subjectId, channel, code);
        }
        if (!sent && !logCodes) {
            throw new BusinessRuleException(channel == KycChannel.MOBILE
                    ? "Text messages are not set up for your company yet. Ask your administrator to set up SMS in Communication settings."
                    : "E-mail is not set up yet. Ask your administrator.");
        }
        return view(subject, refresh(profile, subject));
    }

    @Transactional(noRollbackFor = BusinessRuleException.class)
    public KycView verifyCode(UUID companyId, KycSubjectType type, UUID subjectId, KycChannel channel, String code) {
        KycSubject subject = subject(companyId, type, subjectId);
        String target = target(subject, channel);
        KycProfile profile = profile(companyId, subject);
        Instant now = Instant.now();
        KycOtp otp = otps.findRecent(companyId, profile.getId(), channel, now.minus(CODE_TTL)).stream()
                .filter(o -> o.getConsumedAt() == null && o.getExpiresAt().isAfter(now) && o.getTarget().equals(target))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException("The code has expired. Ask for a new one."));
        if (otp.getAttempts() >= MAX_ATTEMPTS) {
            throw new BusinessRuleException("Too many wrong codes. Ask for a new one.");
        }
        String typed = code == null ? "" : code.trim();
        if (!typed.matches("^[0-9]{6}$") || !encoder.matches(typed, otp.getCodeHash())) {
            otp.setAttempts(otp.getAttempts() + 1);
            otps.save(otp);
            throw new BusinessRuleException("That code is not right. Check it and try again.");
        }
        otp.setConsumedAt(now);
        otps.save(otp);
        if (channel == KycChannel.EMAIL) {
            profile.setEmail(target);
            profile.setEmailVerifiedAt(now);
        } else {
            profile.setMobile(target);
            profile.setMobileVerifiedAt(now);
        }
        auditService.record(AuditAction.KYC_UPDATED, ENTITY, profile.getId(),
                Map.of("subjectType", type.name(), "verified", channel.name()));
        return view(subject, refresh(profile, subject));
    }

    // ------------------------------------------------------------------ internals

    private KycSubject subject(UUID companyId, KycSubjectType type, UUID id) {
        return directory.find(companyId, type, id).orElseThrow(() -> new ResourceNotFoundException(type.name(), id));
    }

    private KycProfile profile(UUID companyId, KycSubject subject) {
        return profiles.findBySubject(companyId, subject.type(), subject.id()).orElseGet(() -> {
            KycProfile p = new KycProfile();
            p.setSubjectType(subject.type());
            p.setSubjectId(subject.id());
            p.setStatus(KycStatus.PENDING);
            return profiles.saveAndFlush(p);
        });
    }

    private static String target(KycSubject subject, KycChannel channel) {
        String value = channel == KycChannel.EMAIL ? subject.email() : subject.mobile();
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException(channel == KycChannel.EMAIL
                    ? "There is no e-mail address on this record. Ask your administrator to add one."
                    : "There is no mobile number on this record. Ask your administrator to add one.");
        }
        return channel == KycChannel.EMAIL ? value.trim().toLowerCase() : value.trim();
    }

    /** Recomputes the status from the documents and verifications; saves only when it changed. */
    private KycProfile refresh(KycProfile p, KycSubject subject) {
        KycView v = view(subject, p);
        KycStatus status = v.requiredUploaded() == v.requiredTotal() && v.emailVerified() && v.mobileVerified()
                ? KycStatus.COMPLETE : KycStatus.PENDING;
        if (status != p.getStatus()) {
            p.setStatus(status);
            p.setCompletedAt(status == KycStatus.COMPLETE ? Instant.now() : null);
            p = profiles.save(p);
            auditService.record(AuditAction.KYC_UPDATED, ENTITY, p.getId(),
                    Map.of("subjectType", subject.type().name(), "status", status.name()));
        }
        return p;
    }

    private KycView view(KycSubject subject, KycProfile p) {
        List<KycDocument> docs = p.getId() == null ? List.of() : documents.findAllOfProfile(p.getCompanyId(), p.getId());
        List<KycView.Slot> slots = new ArrayList<>();
        int required = 0;
        int uploaded = 0;
        for (DocumentSpec spec : KycDocumentCatalog.of(subject.type())) {
            Optional<KycDocument> d = docs.stream().filter(x -> x.getDocumentType().equals(spec.code())).findFirst();
            if (spec.required()) {
                required++;
                if (d.isPresent()) {
                    uploaded++;
                }
            }
            slots.add(new KycView.Slot(spec.code(), spec.label(), spec.hint(), spec.required(),
                    d.map(x -> new KycView.Uploaded(x.getId(), x.getFileName(), x.getContentType(), x.getSizeBytes(),
                            x.getCreatedAt())).orElse(null)));
        }
        String email = subject.email() == null ? null : subject.email().trim().toLowerCase();
        String mobile = subject.mobile() == null ? null : subject.mobile().trim();
        boolean emailOk = p.getEmailVerifiedAt() != null && email != null && Objects.equals(email, p.getEmail());
        boolean mobileOk = p.getMobileVerifiedAt() != null && mobile != null && Objects.equals(mobile, p.getMobile());
        return new KycView(subject.type(), subject.id(), subject.name(), p.getStatus(), email, emailOk, mobile, mobileOk,
                uploaded, required, slots, p.getCompletedAt());
    }
}
