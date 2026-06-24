package com.smartlearnly.backend.auth.service;

public interface EmailService {
    void sendVerificationOtp(String email, String fullName, String otpCode);

    void sendPasswordResetLink(String email, String fullName, String resetLink);

    /**
     * Send a connectivity test email to verify the active email configuration.
     *
     * @throws RuntimeException when the transport is not configured or the send fails
     */
    void sendTestEmail(String email);
}
