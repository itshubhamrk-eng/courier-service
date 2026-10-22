package com.courier.modules.auth.infrastructure;

import com.courier.modules.auth.application.port.NotificationPort;
import com.courier.modules.communication.application.DirectMessageSender;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Account e-mails (password reset, e-mail verification, company activation) sent for real, through the
 * deployment's SMTP e-mail provider. Active when {@code app.communication.email.enabled=true}
 * (with {@code spring.mail.*} set); otherwise the log-only sender is used in development.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.communication.email", name = "enabled", havingValue = "true")
public class EmailNotificationSender implements NotificationPort {

    private final DirectMessageSender sender;

    @Override
    public void sendPasswordResetLink(String email, String displayName, String link) {
        sender.sendEmail(null, email, "Reset your password",
                body(displayName, "We received a request to reset your password.", "Reset password", link,
                        "If you did not ask for this, ignore this e-mail; your password stays the same."));
    }

    @Override
    public void sendEmailVerificationLink(String email, String displayName, String link) {
        sender.sendEmail(null, email, "Confirm your e-mail address",
                body(displayName, "Please confirm your e-mail address.", "Confirm e-mail", link, null));
    }

    @Override
    public void sendCompanyActivation(String email, String displayName, String companyName, String link) {
        sender.sendEmail(null, email, "Activate your " + companyName + " account",
                body(displayName, "An account has been created for you at " + HtmlUtils.htmlEscape(companyName)
                        + ". Activate it to sign in.", "Activate account", link, null));
    }

    private static String body(String name, String intro, String action, String link, String footer) {
        String who = name == null || name.isBlank() ? "there" : HtmlUtils.htmlEscape(name);
        String href = HtmlUtils.htmlEscape(link);
        return "<p>Hello " + who + ",</p><p>" + intro + "</p>"
                + "<p><a href=\"" + href + "\" style=\"display:inline-block;padding:10px 18px;background:#d61f45;color:#fff;"
                + "border-radius:8px;text-decoration:none;font-weight:600\">" + action + "</a></p>"
                + "<p style=\"color:#5d6285;font-size:13px\">Or open this link: " + href + "</p>"
                + (footer == null ? "" : "<p style=\"color:#5d6285;font-size:13px\">" + footer + "</p>");
    }
}
