package com.courier.modules.kyc.application;

import com.courier.modules.kyc.domain.KycChannel;
import com.courier.modules.kyc.domain.KycDocument;
import com.courier.modules.kyc.domain.KycDocumentFile;
import com.courier.modules.kyc.domain.KycDocumentFileRepository;
import com.courier.modules.kyc.domain.KycDocumentRepository;
import com.courier.modules.kyc.domain.KycOtp;
import com.courier.modules.kyc.domain.KycOtpRepository;
import com.courier.modules.kyc.domain.KycProfile;
import com.courier.modules.kyc.domain.KycProfileRepository;
import com.courier.modules.kyc.domain.KycReviewStatus;
import com.courier.modules.kyc.domain.KycStatus;
import com.courier.modules.kyc.domain.KycSubjectType;
import com.courier.shared.audit.application.AuditService;
import com.courier.shared.exception.BusinessRuleException;
import com.courier.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** KYC rules over in-memory repositories: documents, codes, completion, ownership. */
class KycServiceTest {

    private static final UUID COMPANY = UUID.randomUUID();
    private static final UUID CUSTOMER = UUID.randomUUID();
    private static final byte[] PDF = "%PDF-1.7 test".getBytes();
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private final Map<UUID, KycProfile> profileRows = new HashMap<>();
    private final Map<UUID, KycDocument> documentRows = new HashMap<>();
    private final Map<UUID, byte[]> fileRows = new HashMap<>();
    private final List<KycOtp> otpRows = new ArrayList<>();
    private final AtomicReference<String> lastCode = new AtomicReference<>();
    private KycSubject subject = new KycSubject(KycSubjectType.CUSTOMER, CUSTOMER, "Acme Traders", "Ops@Acme.in", "9876543210");
    private boolean smsConfigured = true;
    private KycService service;

    @BeforeEach
    void setUp() {
        KycProfileRepository profiles = mock(KycProfileRepository.class);
        when(profiles.findBySubject(any(), any(), any())).thenAnswer(i -> profileRows.values().stream()
                .filter(p -> p.getCompanyId().equals(i.getArgument(0)) && p.getSubjectType() == i.getArgument(1)
                        && p.getSubjectId().equals(i.getArgument(2))).findFirst());
        when(profiles.saveAndFlush(any())).thenAnswer(i -> stamp(i.getArgument(0)));
        when(profiles.save(any())).thenAnswer(i -> stamp(i.getArgument(0)));

        KycDocumentRepository documents = mock(KycDocumentRepository.class);
        when(documents.saveAndFlush(any())).thenAnswer(i -> {
            KycDocument d = stamp(i.getArgument(0));
            documentRows.put(d.getId(), d);
            return d;
        });
        when(documents.findAllOfProfile(any(), any())).thenAnswer(i -> documentRows.values().stream()
                .filter(d -> d.getProfileId().equals(i.getArgument(1))).toList());
        when(documents.findOfType(any(), any(), anyString())).thenAnswer(i -> documentRows.values().stream()
                .filter(d -> d.getProfileId().equals(i.getArgument(1)) && d.getDocumentType().equals(i.getArgument(2))).findFirst());
        when(documents.findById(any())).thenAnswer(i -> Optional.ofNullable(documentRows.get(i.<UUID>getArgument(0))));
        when(documents.save(any())).thenAnswer(i -> i.getArgument(0));
        doAnswer(i -> documentRows.remove(i.<KycDocument>getArgument(0).getId())).when(documents).delete(any());

        KycDocumentFileRepository files = mock(KycDocumentFileRepository.class);
        when(files.save(any())).thenAnswer(i -> {
            KycDocumentFile f = i.getArgument(0);
            fileRows.put(f.getDocumentId(), f.getContent());
            return f;
        });
        when(files.findById(any())).thenAnswer(i -> Optional.ofNullable(fileRows.get(i.<UUID>getArgument(0)))
                .map(b -> new KycDocumentFile(i.getArgument(0), b)));
        doAnswer(i -> fileRows.remove(i.<UUID>getArgument(0))).when(files).deleteById(any());

        KycOtpRepository otps = mock(KycOtpRepository.class);
        when(otps.save(any())).thenAnswer(i -> {
            KycOtp o = i.getArgument(0);
            if (o.getCreatedAt() == null) {
                stamp(o);
                otpRows.add(o);
            }
            return o;
        });
        when(otps.findRecent(any(), any(), any(), any())).thenAnswer(i -> otpRows.stream()
                .filter(o -> o.getProfileId().equals(i.getArgument(1)) && o.getChannel() == i.getArgument(2)
                        && !o.getCreatedAt().isBefore(i.getArgument(3)))
                .sorted(Comparator.comparing(KycOtp::getCreatedAt).reversed()).toList());

        KycSubjectDirectory directory = mock(KycSubjectDirectory.class);
        when(directory.find(eq(COMPANY), any(), any())).thenAnswer(i ->
                i.getArgument(1) == subject.type() && subject.id().equals(i.getArgument(2)) ? Optional.of(subject) : Optional.empty());

        KycMessenger messenger = new KycMessenger() {
            @Override public boolean emailCode(UUID c, String e, String n, String code) { lastCode.set(code); return true; }
            @Override public boolean smsCode(UUID c, String m, String code) { lastCode.set(code); return smsConfigured; }
        };
        service = new KycService(profiles, documents, files, otps, directory, messenger, new BCryptPasswordEncoder(4),
                mock(AuditService.class));
    }

    private <T extends com.courier.shared.domain.CompanyOwnedEntity> T stamp(T e) {
        if (e.getCompanyId() == null) {
            ReflectionTestUtils.setField(e, "companyId", COMPANY);
        }
        if (e.getCreatedAt() == null) {
            e.setCreatedAt(Instant.now());
        }
        e.setUpdatedAt(Instant.now());
        if (e instanceof KycProfile p) {
            profileRows.put(p.getId(), p);
        }
        return e;
    }

    private void uploadAllRequired() {
        for (KycDocumentCatalog.DocumentSpec d : KycDocumentCatalog.of(KycSubjectType.CUSTOMER)) {
            if (d.required()) {
                service.upload(COMPANY, KycSubjectType.CUSTOMER, CUSTOMER, d.code(), d.code() + ".pdf", PDF, UUID.randomUUID());
            }
        }
    }

    private void verify(KycChannel channel) {
        service.sendCode(COMPANY, KycSubjectType.CUSTOMER, CUSTOMER, channel);
        service.verifyCode(COMPANY, KycSubjectType.CUSTOMER, CUSTOMER, channel, lastCode.get());
    }

    @Test
    @DisplayName("a business customer sees its own document list; nothing is complete at first")
    void checklist() {
        KycView v = service.view(COMPANY, KycSubjectType.CUSTOMER, CUSTOMER);
        assertThat(v.status()).isEqualTo(KycStatus.PENDING);
        assertThat(v.documents()).extracting(KycView.Slot::code).contains("GST_CERTIFICATE", "COMPANY_PAN", "CANCELLED_CHEQUE");
        assertThat(v.requiredUploaded()).isZero();
        assertThat(v.emailVerified()).isFalse();
    }

    private void reviewAll(boolean approve) {
        for (KycView.Slot slot : service.view(COMPANY, KycSubjectType.CUSTOMER, CUSTOMER).documents()) {
            if (slot.uploaded() != null) {
                service.review(COMPANY, KycSubjectType.CUSTOMER, CUSTOMER, slot.uploaded().id(), approve,
                        approve ? null : "Blurred", UUID.randomUUID());
            }
        }
    }

    private KycView.Uploaded uploaded(KycView v, String code) {
        return v.documents().stream().filter(s -> s.code().equals(code)).findFirst().orElseThrow().uploaded();
    }

    @Test
    @DisplayName("everything uploaded and verified waits for review; complete only once every required document is approved")
    void completion() {
        uploadAllRequired();
        verify(KycChannel.EMAIL);
        verify(KycChannel.MOBILE);
        KycView waiting = service.view(COMPANY, KycSubjectType.CUSTOMER, CUSTOMER);
        assertThat(waiting.status()).isEqualTo(KycStatus.UNDER_REVIEW);
        assertThat(uploaded(waiting, "GST_CERTIFICATE").reviewStatus()).isEqualTo(KycReviewStatus.PENDING_REVIEW);

        reviewAll(true);
        KycView done = service.view(COMPANY, KycSubjectType.CUSTOMER, CUSTOMER);
        assertThat(done.status()).isEqualTo(KycStatus.COMPLETE);
        assertThat(done.requiredApproved()).isEqualTo(done.requiredTotal());
        assertThat(done.completedAt()).isNotNull();

        // removing a required document makes it pending again
        UUID gst = uploaded(done, "GST_CERTIFICATE").id();
        assertThat(service.removeDocument(COMPANY, KycSubjectType.CUSTOMER, CUSTOMER, gst).status()).isEqualTo(KycStatus.PENDING);
    }

    @Test
    @DisplayName("a rejected document needs a reason, counts as missing, shows the reason, and a new upload goes back to review")
    void rejection() {
        uploadAllRequired();
        verify(KycChannel.EMAIL);
        verify(KycChannel.MOBILE);
        reviewAll(true);
        UUID pan = uploaded(service.view(COMPANY, KycSubjectType.CUSTOMER, CUSTOMER), "COMPANY_PAN").id();

        assertThatThrownBy(() -> service.review(COMPANY, KycSubjectType.CUSTOMER, CUSTOMER, pan, false, "  ", UUID.randomUUID()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("reason");
        KycView rejected = service.review(COMPANY, KycSubjectType.CUSTOMER, CUSTOMER, pan, false, "Name not readable", UUID.randomUUID());
        assertThat(rejected.status()).isEqualTo(KycStatus.PENDING);
        assertThat(uploaded(rejected, "COMPANY_PAN").reviewStatus()).isEqualTo(KycReviewStatus.REJECTED);
        assertThat(uploaded(rejected, "COMPANY_PAN").reviewNote()).isEqualTo("Name not readable");
        assertThat(rejected.requiredUploaded()).isEqualTo(rejected.requiredTotal() - 1);

        KycView reuploaded = service.upload(COMPANY, KycSubjectType.CUSTOMER, CUSTOMER, "COMPANY_PAN", "pan2.pdf", PDF, null);
        assertThat(reuploaded.status()).isEqualTo(KycStatus.UNDER_REVIEW);
        assertThat(uploaded(reuploaded, "COMPANY_PAN").reviewStatus()).isEqualTo(KycReviewStatus.PENDING_REVIEW);
        assertThat(uploaded(reuploaded, "COMPANY_PAN").reviewNote()).isNull();
    }

    @Test
    @DisplayName("changing the e-mail on the record undoes its verification")
    void emailChange() {
        verify(KycChannel.EMAIL);
        assertThat(service.view(COMPANY, KycSubjectType.CUSTOMER, CUSTOMER).emailVerified()).isTrue();
        subject = new KycSubject(KycSubjectType.CUSTOMER, CUSTOMER, "Acme Traders", "new@acme.in", "9876543210");
        assertThat(service.view(COMPANY, KycSubjectType.CUSTOMER, CUSTOMER).emailVerified()).isFalse();
    }

    @Test
    @DisplayName("uploads are checked by content: PDF/PNG accepted, anything else or over 5 MB refused; same type replaces")
    void uploads() {
        service.upload(COMPANY, KycSubjectType.CUSTOMER, CUSTOMER, "COMPANY_PAN", "../../pan.png", PNG, null);
        KycView v = service.upload(COMPANY, KycSubjectType.CUSTOMER, CUSTOMER, "COMPANY_PAN", "pan.pdf", PDF, null);
        KycView.Uploaded up = v.documents().stream().filter(s -> s.code().equals("COMPANY_PAN")).findFirst().orElseThrow().uploaded();
        assertThat(up.contentType()).isEqualTo("application/pdf");
        assertThat(documentRows).hasSize(1);
        assertThat(service.file(COMPANY, KycSubjectType.CUSTOMER, CUSTOMER, up.id()).content()).isEqualTo(PDF);

        assertThatThrownBy(() -> service.upload(COMPANY, KycSubjectType.CUSTOMER, CUSTOMER, "COMPANY_PAN", "x.html",
                "<html><script>".getBytes(), null)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("PDF, JPEG or PNG");
        byte[] big = new byte[KycFiles.MAX_BYTES + 1];
        System.arraycopy(PDF, 0, big, 0, PDF.length);
        assertThatThrownBy(() -> service.upload(COMPANY, KycSubjectType.CUSTOMER, CUSTOMER, "COMPANY_PAN", "big.pdf", big, null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("5 MB");
        assertThatThrownBy(() -> service.upload(COMPANY, KycSubjectType.CUSTOMER, CUSTOMER, "PHOTO", "p.pdf", PDF, null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Unknown document type");
    }

    @Test
    @DisplayName("codes: wrong code counts an attempt, five wrong locks it, resend waits a minute")
    void codes() {
        service.sendCode(COMPANY, KycSubjectType.CUSTOMER, CUSTOMER, KycChannel.MOBILE);
        assertThatThrownBy(() -> service.sendCode(COMPANY, KycSubjectType.CUSTOMER, CUSTOMER, KycChannel.MOBILE))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Wait a minute");
        String wrong = lastCode.get().equals("000000") ? "111111" : "000000";
        for (int i = 0; i < KycService.MAX_ATTEMPTS; i++) {
            assertThatThrownBy(() -> service.verifyCode(COMPANY, KycSubjectType.CUSTOMER, CUSTOMER, KycChannel.MOBILE, wrong))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("not right");
        }
        assertThatThrownBy(() -> service.verifyCode(COMPANY, KycSubjectType.CUSTOMER, CUSTOMER, KycChannel.MOBILE, lastCode.get()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Too many wrong codes");
        assertThat(otpRows).allMatch(o -> !o.getCodeHash().contains(lastCode.get()));
    }

    @Test
    @DisplayName("SMS not set up for the company: a clear message, not a silent success")
    void smsNotConfigured() {
        smsConfigured = false;
        assertThatThrownBy(() -> service.sendCode(COMPANY, KycSubjectType.CUSTOMER, CUSTOMER, KycChannel.MOBILE))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("not set up");
    }

    @Test
    @DisplayName("another company's or an unknown subject is not found; a document of another profile cannot be read")
    void ownership() {
        assertThatThrownBy(() -> service.view(UUID.randomUUID(), KycSubjectType.CUSTOMER, CUSTOMER))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.view(COMPANY, KycSubjectType.USER, CUSTOMER)).isInstanceOf(ResourceNotFoundException.class);
        KycView v = service.upload(COMPANY, KycSubjectType.CUSTOMER, CUSTOMER, "COMPANY_PAN", "pan.pdf", PDF, null);
        UUID doc = v.documents().stream().filter(s -> s.uploaded() != null).findFirst().orElseThrow().uploaded().id();
        UUID other = UUID.randomUUID();
        subject = new KycSubject(KycSubjectType.CUSTOMER, other, "Other", "o@x.in", "9000000000");
        service.view(COMPANY, KycSubjectType.CUSTOMER, other);
        assertThatThrownBy(() -> service.file(COMPANY, KycSubjectType.CUSTOMER, other, doc)).isInstanceOf(ResourceNotFoundException.class);
    }
}
