package com.smartlearnly.backend.auth.service;

public interface EmailService {
    void sendVerificationOtp(String email, String fullName, String otpCode);

    void sendPasswordResetLink(String email, String fullName, String resetLink);
}
