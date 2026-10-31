package com.alcontable.portfolio.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "accounting_users",
        indexes = {
                @Index(name = "idx_acc_user_email", columnList = "email", unique = true),
                @Index(name = "idx_acc_user_token", columnList = "session_token")
        }
)
public class AccountantUserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, length = 140, unique = true)
    private String email;

    @Column(name = "full_name", nullable = false, length = 140)
    private String fullName;

    @Column(name = "firm_name", nullable = false, length = 160)
    private String firmName;

    @Column(name = "professional_card", nullable = false, length = 60)
    private String professionalCard;

    @Column(name = "password_hash", nullable = false, length = 128)
    private String passwordHash;

    @Column(name = "session_token", length = 128)
    private String sessionToken;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "role", length = 60)
    private String role = "CONTADOR_TITULAR";

    public AccountantUserEntity() {
    }

    public AccountantUserEntity(
            String email,
            String fullName,
            String firmName,
            String professionalCard,
            String passwordHash,
            String sessionToken
    ) {
        this.email = email;
        this.fullName = fullName;
        this.firmName = firmName;
        this.professionalCard = professionalCard;
        this.passwordHash = passwordHash;
        this.sessionToken = sessionToken;
        this.role = "CONTADOR_TITULAR";
        this.createdAt = LocalDateTime.now();
    }

    public AccountantUserEntity(
            String email,
            String fullName,
            String professionalCard,
            String firmName,
            String passwordHash,
            String role,
            String sessionToken
    ) {
        this.email = email;
        this.fullName = fullName;
        this.professionalCard = professionalCard;
        this.firmName = firmName;
        this.passwordHash = passwordHash;
        this.role = role != null ? role : "CONTADOR_TITULAR";
        this.sessionToken = sessionToken;
        this.createdAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.role == null || this.role.isBlank()) {
            this.role = "CONTADOR_TITULAR";
        }
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getFirmName() {
        return firmName;
    }

    public void setFirmName(String firmName) {
        this.firmName = firmName;
    }

    public String getProfessionalCard() {
        return professionalCard;
    }

    public String getProfessionalCardNumber() {
        return professionalCard;
    }

    public void setProfessionalCard(String professionalCard) {
        this.professionalCard = professionalCard;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public String getAuthToken() {
        return sessionToken;
    }

    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    public void setAuthToken(String authToken) {
        this.sessionToken = authToken;
    }

    public String getRole() {
        return role != null ? role : "CONTADOR_TITULAR";
    }

    public void setRole(String role) {
        this.role = role;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
