package com.theotech.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Access to the authenticated principal from anywhere in the service layer. */
@Component
public class CurrentUser {

    public AppUserPrincipal principalOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppUserPrincipal p) {
            return p;
        }
        return null;
    }

    public AppUserPrincipal require() {
        AppUserPrincipal p = principalOrNull();
        if (p == null) {
            throw new AuthenticationCredentialsNotFoundException("No authenticated user");
        }
        return p;
    }

    public Long idOrNull() {
        AppUserPrincipal p = principalOrNull();
        return p == null ? null : p.getId();
    }

    public boolean has(String permission) {
        AppUserPrincipal p = principalOrNull();
        return p != null && p.hasPermission(permission);
    }
}
