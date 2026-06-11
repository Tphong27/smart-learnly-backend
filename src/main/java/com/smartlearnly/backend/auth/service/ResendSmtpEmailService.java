package com.smartlearnly.backend.auth.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Service
public class ResendSmtpEmailService implements EmailService {
    private static final Logger log = LoggerFactory.getLogger(ResendSmtpEmailService.class);

    private final JavaMailSender mailSender;
    private final String smtpPassword;
    private final String fromAddress;

    public ResendSmtpEmailService(
            JavaMailSender mailSender,
            @Value("${spring.mail.password:}") String smtpPassword,
            @Value("${app.email.from:Smart Learnly <no-reply@mail.smartlearnly.online>}") String fromAddress
    ) {
        this.mailSender = mailSender;
        this.smtpPassword = smtpPassword;
        this.fromAddress = fromAddress;
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendVerificationOtp(String email, String fullName, String otpCode) {
        send(
                email,
                "Verify your Smart Learnly account",
                buildOtpEmailHtml(fullName, otpCode)
        );
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendPasswordResetLink(String email, String fullName, String resetLink) {
        send(
                email,
                "Reset your Smart Learnly password",
                buildLinkEmailHtml(
                        fullName,
                        "Reset your password",
                        "Use the link below to choose a new password for your Smart Learnly account.",
                        "Reset password",
                        resetLink
                )
        );
    }

    private void send(String to, String subject, String html) {
        if (smtpPassword == null || smtpPassword.isBlank()) {
            log.info("Resend SMTP is not configured. Skipping email to={} subject={}", to, subject);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
        }
        catch (MessagingException exception) {
            throw new IllegalStateException("Could not prepare email for SMTP delivery", exception);
        }
    }

    private String buildOtpEmailHtml(String fullName, String otpCode) {
        String safeName = HtmlUtils.htmlEscape(fullName);
        String safeOtp = HtmlUtils.htmlEscape(otpCode);
        return """
                <!doctype html>
                <html lang="en">
                  <body style="font-family:Arial,sans-serif;color:#172033;line-height:1.6">
                    <div style="max-width:560px;margin:0 auto;padding:32px">
                      <h1 style="font-size:24px">Smart Learnly</h1>
                      <h2 style="font-size:20px">Verify your email</h2>
                      <p>Hello %s,</p>
                      <p>Enter this one-time code to activate your account:</p>
                      <p style="font-size:32px;font-weight:700;letter-spacing:8px">%s</p>
                      <p style="font-size:13px;color:#64748b">This code expires in 15 minutes and can be used once.</p>
                    </div>
                  </body>
                </html>
                """.formatted(safeName, safeOtp);
    }

    private String buildLinkEmailHtml(String fullName, String heading, String message, String buttonText, String link) {
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
}
