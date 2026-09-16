package com.theotech.iam.domain;

import com.theotech.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/** Authentication identity. HR data lives in {@code employees} (Phase 7) with a nullable FK back here. */
@Entity
@Table(name = "users")
public class User extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    private String email;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    private String phone;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private boolean locked;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "preferred_language", nullable = false)
    private String preferredLanguage = "en";

    @Column(name = "default_branch_id")
    private Long defaultBranchId;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    protected User() {
    }

    public User(String username, String fullName, String passwordHash) {
        this.username = username;
        this.fullName = fullName;
        this.passwordHash = passwordHash;
    }

    // ---- lockout & login bookkeeping ------------------------------------------------------

    /** True when the account is usable right now (not manually locked and no active automatic lock). */
    public boolean isAccountNonLocked() {
        return !locked && (lockedUntil == null || lockedUntil.isBefore(Instant.now()));
    }

    /** Records a failed attempt; returns true if this attempt triggered an automatic lock. */
    public boolean registerFailedLogin(int maxAttempts, Instant lockUntil) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= maxAttempts) {
            lockedUntil = lockUntil;
            failedLoginAttempts = 0;
            return true;
        }
        return false;
    }

    public void registerSuccessfulLogin(Instant at) {
        failedLoginAttempts = 0;
        lockedUntil = null;
        lastLoginAt = at;
    }

    public void changePassword(String newHash, Instant at) {
        this.passwordHash = newHash;
        this.passwordChangedAt = at;
        this.mustChangePassword = false;
    }

    public void unlock() {
        this.locked = false;
        this.lockedUntil = null;
        this.failedLoginAttempts = 0;
    }

    // ---- accessors -------------------------------------------------------------------------

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
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

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public void setMustChangePassword(boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    public Instant getPasswordChangedAt() {
        return passwordChangedAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public String getPreferredLanguage() {
        return preferredLanguage;
    }

    public void setPreferredLanguage(String preferredLanguage) {
        this.preferredLanguage = preferredLanguage;
    }

    public Long getDefaultBranchId() {
        return defaultBranchId;
    }

    public void setDefaultBranchId(Long defaultBranchId) {
        this.defaultBranchId = defaultBranchId;
    }

    public Set<Role> getRoles() {
        return roles;
    }
}
