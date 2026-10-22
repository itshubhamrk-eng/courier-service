package com.courier.modules.kyc.infrastructure;

import com.courier.modules.communication.application.DirectMessageSender;
import com.courier.modules.kyc.application.KycMessenger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.util.UUID;

/** Codes by e-mail (deployment SMTP) and SMS (the company's SMS channel). */
@Slf4j
@Component
@RequiredArgsConstructor
public class KycMessengerAdapter implements KycMessenger {

    private final DirectMessageSender sender;

    @Override
    public boolean emailCode(UUID companyId, String email, String name, String code) {
        String who = name == null || name.isBlank() ? "there" : HtmlUtils.htmlEscape(name);
        sender.sendEmail(companyId, email, "Your KYC verification code: " + code,
                "<p>Hello " + who + ",</p><p>Your verification code is <strong style=\"font-size:20px;letter-spacing:3px\">"
                        + code + "</strong>.</p><p>It is valid for 10 minutes. If you did not ask for it, ignore this e-mail.</p>");
        return true;
    }

    @Override
    public boolean smsCode(UUID companyId, String mobile, String code) {
        return sender.sendSms(companyId, mobile, code + " is your KYC verification code. Valid for 10 minutes. Do not share it.");
    }
}
