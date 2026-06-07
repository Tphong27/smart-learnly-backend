package com.smartlearnly.backend.auth.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "password_reset_tokens", schema = "public")
public class PasswordResetToken extends AbstractAuthToken {
}
