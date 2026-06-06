package com.smartlearnly.backend.auth.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "email_verification_tokens", schema = "public")
public class EmailVerificationToken extends AbstractAuthToken {
}
