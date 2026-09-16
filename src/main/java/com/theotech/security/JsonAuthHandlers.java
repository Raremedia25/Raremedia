package com.theotech.security;

import com.theotech.common.ApiResponse;
import com.theotech.security.dto.MeResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Every security outcome answers with the JSON envelope instead of a redirect or an HTML error page:
 * login success/failure, logout, "not authenticated" on {@code /api/**}, and access denied.
 */
@Component
public class JsonAuthHandlers implements AuthenticationSuccessHandler, AuthenticationFailureHandler,
        LogoutSuccessHandler, AuthenticationEntryPoint, AccessDeniedHandler {

    private final JsonMapper json;
    private final LoginAttemptService loginAttempts;

    public JsonAuthHandlers(JsonMapper json, LoginAttemptService loginAttempts) {
        this.json = json;
        this.loginAttempts = loginAttempts;
    }

    // ---- login success ---------------------------------------------------------------------

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
        loginAttempts.onSuccess(principal);
        write(response, HttpStatus.OK, ApiResponse.ok(MeResponse.from(principal)));
    }

    // ---- login failure ---------------------------------------------------------------------

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        String username = request.getParameter("username");
        String code;
        if (exception instanceof LockedException) {
            loginAttempts.onRejected(username, "Account is locked");
            code = "ACCOUNT_LOCKED";
        } else if (exception instanceof DisabledException) {
            loginAttempts.onRejected(username, "Account is disabled");
            code = "ACCOUNT_DISABLED";
        } else if (exception instanceof BadCredentialsException) {
            code = loginAttempts.onBadCredentials(username) ? "ACCOUNT_LOCKED" : "BAD_CREDENTIALS";
        } else {
            loginAttempts.onRejected(username, exception.getClass().getSimpleName());
            code = "AUTH_FAILED";
        }
        write(response, HttpStatus.UNAUTHORIZED, ApiResponse.error(code, "Authentication failed"));
    }

    // ---- logout ----------------------------------------------------------------------------

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response,
                                Authentication authentication) throws IOException {
        if (authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal p) {
            loginAttempts.onLogout(p);
        }
        write(response, HttpStatus.OK, ApiResponse.ok());
    }

    // ---- not authenticated (entry point for /api/**) ---------------------------------------

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(response, HttpStatus.UNAUTHORIZED, ApiResponse.error("UNAUTHENTICATED", "Authentication required"));
    }

    // ---- access denied ---------------------------------------------------------------------

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        if (accessDeniedException instanceof CsrfException) {
            write(response, HttpStatus.FORBIDDEN, ApiResponse.error("CSRF_INVALID", "Missing or invalid CSRF token"));
        } else {
            write(response, HttpStatus.FORBIDDEN, ApiResponse.error("FORBIDDEN", "You are not allowed to perform this action"));
        }
    }

    // ---- helpers ---------------------------------------------------------------------------

    public void write(HttpServletResponse response, HttpStatus status, Object body) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(json.writeValueAsString(body));
        response.getWriter().flush();
    }
}
