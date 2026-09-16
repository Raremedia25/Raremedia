package com.theotech.security;

import com.theotech.common.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * While a user's account is flagged {@code must_change_password} (seeded accounts, admin resets),
 * every API call except the ones needed to change it is refused with {@code PASSWORD_CHANGE_REQUIRED}.
 * Registered inside the security filter chain (not as a servlet filter) by {@link SecurityConfig}.
 */
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED = Set.of(
            "/api/auth/me", "/api/auth/change-password", "/api/auth/logout", "/api/auth/csrf");

    private final JsonAuthHandlers handlers;

    public PasswordChangeRequiredFilter(JsonAuthHandlers handlers) {
        this.handlers = handlers;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppUserPrincipal p && p.isMustChangePassword()) {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            if (path.startsWith("/api/") && !ALLOWED.contains(path)) {
                handlers.write(response, HttpStatus.FORBIDDEN,
                        ApiResponse.error("PASSWORD_CHANGE_REQUIRED", "You must change your password before continuing"));
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
