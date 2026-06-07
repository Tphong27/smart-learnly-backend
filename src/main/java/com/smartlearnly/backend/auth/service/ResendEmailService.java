package com.smartlearnly.backend.auth.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.HtmlUtils;

@Service
public class ResendEmailService implements EmailService {
    private static final Logger log = LoggerFactory.getLogger(ResendEmailService.class);

    private final RestClient resendClient;
    private final String apiKey;
    private final String fromAddress;

    public ResendEmailService(
            @Value("${app.resend.api-url:https://api.resend.com}") String apiUrl,
            @Value("${app.resend.api-key:}") String apiKey,
            @Value("${app.resend.from-email:Smart Learnly <no-reply@mail.smartlearnly.online>}") String fromAddress
    ) {
        this.resendClient = RestClient.create(apiUrl);
        this.apiKey = apiKey;
        this.fromAddress = fromAddress;
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

    private void send(String to, String subject, String html) {
        if (apiKey == null || apiKey.isBlank()) {
            log.info("Resend is not configured. Email fallback to={} subject={} content={}", to, subject, html);
            return;
        }

        resendClient.post()
                .uri("/emails")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ResendEmailRequest(fromAddress, List.of(to), subject, html))
                .retrieve()
                .toBodilessEntity();
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
