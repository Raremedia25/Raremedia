package com.theotech.iam.dto;

import com.theotech.iam.domain.User;

import java.time.Instant;

/** A worker account as the admin sees it. Never includes password material. */
public record WorkerResponse(
        Long id,
        String username,
        String fullName,
        boolean enabled,
        boolean locked,
        boolean mustChangePassword,
        Instant lastLoginAt,
        Instant createdAt) {

    public static WorkerResponse from(User u) {
        return new WorkerResponse(u.getId(), u.getUsername(), u.getFullName(), u.isEnabled(), !u.isAccountNonLocked(),
                u.isMustChangePassword(), u.getLastLoginAt(), u.getCreatedAt());
    }
}
