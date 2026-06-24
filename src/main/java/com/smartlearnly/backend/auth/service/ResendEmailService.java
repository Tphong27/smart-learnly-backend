package com.smartlearnly.backend.auth.service;

import com.smartlearnly.backend.admin.settings.service.SystemSettingsService;
import com.smartlearnly.backend.admin.settings.service.SystemSettingsService.EmailSettings;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.HtmlUtils;

@Service
public class ResendEmailService implements EmailService {
    private static final Logger log = LoggerFactory.getLogger(ResendEmailService.class);

    private final SystemSettingsService settingsService;

    public ResendEmailService(SystemSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @Override
    public void sendVerificationLink(String email, String fullName, String verificationLink) {
        send(
                email,
                "Verify your Smart Learnly account",
                buildEmailHtml(
                        fullName,
                        "Verify your email",
                        "Please verify your email address to activate your Smart Learnly account.",
                        "Verify email",
                        verificationLink
                )
        );
    }

    @Override
    public void sendPasswordResetLink(String email, String fullName, String resetLink) {
        send(
                email,
                "Reset your Smart Learnly password",
                buildEmailHtml(
                        fullName,
                        "Reset your password",
                        "Use the link below to choose a new password for your Smart Learnly account.",
                        "Reset password",
                        resetLink
                )
        );
    }

    @Override
    public void sendTestEmail(String email) {
        EmailSettings settings = settingsService.resolveEmailSettings();
        if (!settings.isConfigured()) {
            throw new IllegalStateException("Email transport is not configured. Set an API key first.");
        }
        dispatch(
                settings,
                email,
                "Smart Learnly email configuration test",
                buildEmailHtml(
                        "there",
                        "Email configuration test",
                        "This is a test email confirming your Smart Learnly email settings are working.",
                        "Open Smart Learnly",
                        "https://smartlearnly.online"
                )
        );
    }

    private void send(String to, String subject, String html) {
        EmailSettings settings = settingsService.resolveEmailSettings();
        if (!settings.isConfigured()) {
            log.info("Email transport is not configured. Email fallback to={} subject={}", to, subject);
            return;
        }
        try {
            dispatch(settings, to, subject, html);
        } catch (RuntimeException exception) {
            // Email delivery is a best-effort side task. A provider failure (e.g. unverified
            // domain) must not break the calling flow (registration, password reset) or crash
            // startup seeding. Log without leaking secrets and continue.
            log.error("Failed to send email to={} subject={}: {}", to, subject, exception.getMessage());
        }
    }

    private void dispatch(EmailSettings settings, String to, String subject, String html) {
        RestClient client = RestClient.create(settings.apiUrl());
        try {
            client.post()
                    .uri("/emails")
                    .header("Authorization", "Bearer " + settings.apiKey())
                    .header("Content-Type", "application/json")
                    .body(new ResendEmailRequest(settings.fromAddress(), List.of(to), subject, html))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            // Surface the provider's own message (e.g. unverified domain) for actionable feedback.
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, extractProviderMessage(exception));
        }
    }

    private String extractProviderMessage(RestClientResponseException exception) {
        try {
            String body = exception.getResponseBodyAsString();
            int messageIndex = body.indexOf("\"message\"");
            if (messageIndex >= 0) {
                int colon = body.indexOf(':', messageIndex);
                int firstQuote = body.indexOf('"', colon + 1);
                int secondQuote = body.indexOf('"', firstQuote + 1);
                if (firstQuote >= 0 && secondQuote > firstQuote) {
                    return body.substring(firstQuote + 1, secondQuote);
                }
            }
        } catch (RuntimeException ignored) {
            // Fall through to generic message.
        }
        return "Email provider rejected the request (status " + exception.getStatusText() + ")";
    }

    private String buildEmailHtml(String fullName, String heading, String message, String buttonText, String link) {
        String safeName = HtmlUtils.htmlEscape(fullName);
        String safeLink = HtmlUtils.htmlEscape(link);

        return """
                <!doctype html>
                <html lang="en">
                  <body style="font-family:Arial,sans-serif;color:#172033;line-height:1.6">
                    <div style="max-width:560px;margin:0 auto;padding:32px">
                      <h1 style="font-size:24px">Smart Learnly</h1>
                      <h2 style="font-size:20px">%s</h2>
                      <p>Hello %s,</p>
                      <p>%s</p>
                      <p style="margin:28px 0">
                        <a href="%s" style="background:#2563eb;color:#fff;padding:12px 20px;text-decoration:none;border-radius:6px">%s</a>
                      </p>
                      <p style="font-size:13px;color:#64748b">If the button does not work, open this link: %s</p>
                    </div>
                  </body>
                </html>
                """.formatted(heading, safeName, message, safeLink, buttonText, safeLink);
    }

    private record ResendEmailRequest(String from, List<String> to, String subject, String html) {
    }
}
