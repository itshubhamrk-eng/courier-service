package com.courier.modules.communication.application;

import com.courier.modules.communication.application.provider.EmailProvider;
import com.courier.modules.communication.application.provider.SmsProvider;
import com.courier.modules.communication.domain.CommunicationChannel;
import com.courier.modules.communication.domain.CommunicationSetting;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Sends one message straight away (no template, no outbox) — for one-time codes and account e-mails, which must
 * arrive now or not at all. E-mail goes through the deployment's {@link EmailProvider} (SMTP when
 * {@code app.communication.email.enabled}, otherwise log-only), from the company's own sender identity when its
 * e-mail channel is set up, else the platform sender. SMS needs the company's SMS channel set up.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DirectMessageSender {

    private final EmailProvider emailProvider;
    private final SmsProvider smsProvider;
    private final CommunicationSettingService settings;
    private final ObjectMapper objectMapper;

    @Value("${app.communication.email.from-address:${spring.mail.username:no-reply@occplsoftech.com}}")
    private String platformFromAddress;

    @Value("${app.communication.email.from-name:OCCPL}")
    private String platformFromName;

    /** Sends an e-mail; a provider failure propagates (the caller tells the person to try again). */
    public void sendEmail(UUID companyId, String to, String subject, String html) {
        Optional<CommunicationSetting> setting = companyId == null ? Optional.empty()
                : settings.findEnabled(companyId, CommunicationChannel.EMAIL);
        Map<String, String> config = setting.map(s -> CommunicationConfigJson.read(objectMapper, s.getConfigJson())).orElse(Map.of());
        String fromEmail = blankToNull(config.get("fromEmail"));
        String fromName = blankToNull(config.get("fromName"));
        emailProvider.send(new EmailProvider.EmailMessage(to, subject, html),
                new EmailProvider.EmailIdentity(fromName == null ? platformFromName : fromName,
                        fromEmail == null ? platformFromAddress : fromEmail));
    }

    /**
     * Sends an SMS through the company's SMS channel.
     *
     * @return false when the company has no SMS channel set up (nothing was sent)
     */
    public boolean sendSms(UUID companyId, String mobile, String text) {
        Optional<CommunicationSetting> setting = settings.findEnabled(companyId, CommunicationChannel.SMS);
        if (setting.isEmpty()) {
            return false;
        }
        Map<String, String> config = CommunicationConfigJson.read(objectMapper, setting.get().getConfigJson());
        SmsProvider.SmsCredentials credentials = new SmsProvider.SmsCredentials(setting.get().getProvider(),
                config.get("apiUrl"), setting.get().getSecret(), config.get("senderId"));
        smsProvider.send(new SmsProvider.SmsMessage(e164(mobile), text), credentials);
        return true;
    }

    /** A 10-digit Indian number gets +91; anything already with a country code keeps it. */
    static String e164(String mobile) {
        String digits = mobile == null ? "" : mobile.replaceAll("[^0-9]", "");
        if (digits.length() == 10) {
            return "+91" + digits;
        }
        if (digits.length() == 11 && digits.startsWith("0")) {
            return "+91" + digits.substring(1);
        }
        return "+" + digits;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
