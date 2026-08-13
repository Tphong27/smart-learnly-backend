package com.smartlearnly.backend.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateAdminUserRequest(
                @Size(max = 150, message = "Full name must not exceed 150 characters") String fullName,

                @Email(message = "Email must be a valid email address") @Size(max = 255, message = "Email must not exceed 255 characters") String email,

                @Size(max = 20, message = "Phone number must not exceed 20 characters") @Pattern(regexp = "^(?:$|(?:0|\\+84)[35789][0-9]{8})$", message = "Phone number must be a valid Vietnamese mobile number, for example 0901234567 or +84901234567") String phoneNumber,

                @Pattern(regexp = "(?i)^(GUEST|TRAINEE|TRAINER|TMO|SME|ADMIN)$", message = "Role is invalid") String role,

                @Pattern(regexp = "(?i)^(pending_verify|active|inactive|banned)$", message = "Status is invalid") String status,

                Boolean emailVerified) {
}
