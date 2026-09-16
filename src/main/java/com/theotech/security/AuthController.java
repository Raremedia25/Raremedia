package com.theotech.security;

import com.theotech.common.ApiResponse;
import com.theotech.security.dto.ChangePasswordRequest;
import com.theotech.security.dto.MeResponse;
import jakarta.validation.Valid;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Session endpoints. Login ({@code POST /api/auth/login}, form-encoded) and logout
 * ({@code POST /api/auth/logout}) are handled by the security filter chain, see {@link SecurityConfig}.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final CurrentUser currentUser;
    private final AuthService authService;

    public AuthController(CurrentUser currentUser, AuthService authService) {
        this.currentUser = currentUser;
        this.authService = authService;
    }

    @GetMapping("/me")
    public ApiResponse<MeResponse> me() {
        return ApiResponse.ok(MeResponse.from(currentUser.require()));
    }

    /** Forces the XSRF-TOKEN cookie to be issued; handy for the login page and for API clients. */
    @GetMapping("/csrf")
    public ApiResponse<Map<String, String>> csrf(CsrfToken token) {
        return ApiResponse.ok(Map.of("headerName", token.getHeaderName(), "parameterName", token.getParameterName()));
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(currentUser.require(), request.currentPassword(), request.newPassword());
        return ApiResponse.ok();
    }
}
