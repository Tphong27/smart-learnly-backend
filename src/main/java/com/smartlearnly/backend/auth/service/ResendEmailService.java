package com.smartlearnly.backend.auth.service;

/*
 * DISABLED AFTER MERGE: the project moved email delivery to SMTP via
 * ResendSmtpEmailService (JavaMailSender). This Resend HTTP-API implementation
 * is kept for reference only. It is commented out so it no longer registers a
 * duplicate EmailService bean and no longer references the removed
 * sendVerificationLink interface method. Do not delete.
 *
 * import com.smartlearnly.backend.admin.settings.service.SystemSettingsService;
 * import com.smartlearnly.backend.admin.settings.service.SystemSettingsService.EmailSettings;
 * import com.smartlearnly.backend.common.exception.BusinessException;
 * import com.smartlearnly.backend.common.exception.ErrorCode;
 * import java.util.List;
 *
 * import org.slf4j.Logger;
 * import org.slf4j.LoggerFactory;
 * import org.springframework.stereotype.Service;
 * import org.springframework.web.client.RestClient;
 * import org.springframework.web.client.RestClientResponseException;
 * import org.springframework.web.util.HtmlUtils;
 *
 * (at)Service
 * public class ResendEmailService implements EmailService {
 *     private static final Logger log = LoggerFactory.getLogger(ResendEmailService.class);
 *
 *     private final SystemSettingsService settingsService;
 *
 *     public ResendEmailService(SystemSettingsService settingsService) {
 *         this.settingsService = settingsService;
 *     }
 *
 *     public void sendVerificationLink(String email, String fullName, String verificationLink) {
 *         send(
 *                 email,
 *                 "Verify your Smart Learnly account",
 *                 buildEmailHtml(
 *                         fullName,
 *                         "Verify your email",
 *                         "Please verify your email address to activate your Smart Learnly account.",
 *                         "Verify email",
 *                         verificationLink
 *                 )
 *         );
 *     }
 *
 *     public void sendPasswordResetLink(String email, String fullName, String resetLink) {
 *         send(
 *                 email,
 *                 "Reset your Smart Learnly password",
 *                 buildEmailHtml(
 *                         fullName,
 *                         "Reset your password",
 *                         "Use the link below to choose a new password for your Smart Learnly account.",
 *                         "Reset password",
 *                         resetLink
 *                 )
 *         );
 *     }
 *
 *     public void sendTestEmail(String email) {
 *         EmailSettings settings = settingsService.resolveEmailSettings();
 *         if (!settings.isConfigured()) {
 *             throw new IllegalStateException("Email transport is not configured. Set an API key first.");
 *         }
 *         dispatch(
 *                 settings,
 *                 email,
 *                 "Smart Learnly email configuration test",
 *                 buildEmailHtml(
 *                         "there",
 *                         "Email configuration test",
 *                         "This is a test email confirming your Smart Learnly email settings are working.",
 *                         "Open Smart Learnly",
 *                         "https://smartlearnly.online"
 *                 )
 *         );
 *     }
 *
 *     private void send(String to, String subject, String html) {
 *         EmailSettings settings = settingsService.resolveEmailSettings();
 *         if (!settings.isConfigured()) {
 *             log.info("Email transport is not configured. Email fallback to={} subject={}", to, subject);
 *             return;
 *         }
 *         try {
 *             dispatch(settings, to, subject, html);
 *         } catch (RuntimeException exception) {
 *             log.error("Failed to send email to={} subject={}: {}", to, subject, exception.getMessage());
 *         }
 *     }
 *
 *     private void dispatch(EmailSettings settings, String to, String subject, String html) {
 *         RestClient client = RestClient.create(settings.apiUrl());
 *         try {
 *             client.post()
 *                     .uri("/emails")
 *                     .header("Authorization", "Bearer " + settings.apiKey())
 *                     .header("Content-Type", "application/json")
 *                     .body(new ResendEmailRequest(settings.fromAddress(), List.of(to), subject, html))
 *                     .retrieve()
 *                     .toBodilessEntity();
 *         } catch (RestClientResponseException exception) {
 *             throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, extractProviderMessage(exception));
 *         }
 *     }
 *
 *     private String extractProviderMessage(RestClientResponseException exception) {
 *         try {
 *             String body = exception.getResponseBodyAsString();
 *             int messageIndex = body.indexOf("message");
 *             if (messageIndex >= 0) {
 *                 int colon = body.indexOf(':', messageIndex);
 *                 int firstQuote = body.indexOf('"', colon + 1);
 *                 int secondQuote = body.indexOf('"', firstQuote + 1);
 *                 if (firstQuote >= 0 && secondQuote > firstQuote) {
 *                     return body.substring(firstQuote + 1, secondQuote);
 *                 }
 *             }
 *         } catch (RuntimeException ignored) {
 *         }
 *         return "Email provider rejected the request (status " + exception.getStatusText() + ")";
 *     }
 *
 *     private String buildEmailHtml(String fullName, String heading, String message, String buttonText, String link) {
 *         String safeName = HtmlUtils.htmlEscape(fullName);
 *         String safeLink = HtmlUtils.htmlEscape(link);
 *         // (HTML email template omitted in disabled reference copy.)
 *         return "";
 *     }
 *
 *     private record ResendEmailRequest(String from, List<String> to, String subject, String html) {
 *     }
 * }
 */
