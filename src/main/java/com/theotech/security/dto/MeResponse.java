package com.theotech.security.dto;

import com.theotech.security.AppUserPrincipal;

import java.util.Set;

/** What the frontend needs to know about the signed-in user. Never includes password material. */
public record MeResponse(Long id, String username, String fullName, Set<String> roles, boolean mustChangePassword) {

    public static MeResponse from(AppUserPrincipal p) {
        return new MeResponse(p.getId(), p.getUsername(), p.getFullName(), p.getRoles(), p.isMustChangePassword());
    }
}
