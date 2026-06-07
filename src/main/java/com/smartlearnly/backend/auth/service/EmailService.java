package com.smartlearnly.backend.auth.service;

public interface EmailService {
    void sendVerificationLink(String email, String fullName, String verificationLink);

    void sendPasswordResetLink(String email, String fullName, String resetLink);
}
