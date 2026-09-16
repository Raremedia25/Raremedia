package com.theotech.security;

import com.theotech.common.exception.NotFoundException;
import com.theotech.common.exception.ValidationException;
import com.theotech.iam.domain.User;
import com.theotech.iam.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void changePassword(AppUserPrincipal principal, String currentPassword, String newPassword) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new NotFoundException("User", principal.getId()));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ValidationException("Current password is incorrect",
                    Map.of("currentPassword", "passwordIncorrect"));
        }
        validateStrength(newPassword);
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new ValidationException("New password must differ from the current one",
                    Map.of("newPassword", "passwordSameAsOld"));
        }

        user.changePassword(passwordEncoder.encode(newPassword), Instant.now());
        principal.passwordChanged();
    }

    /** At least 8 characters with both a letter and a digit. */
    public static void validateStrength(String password) {
        if (password == null || password.length() < 8) {
            throw new ValidationException("Password too short", Map.of("newPassword", "passwordLength"));
        }
        boolean letter = false, digit = false;
        for (char c : password.toCharArray()) {
            if (Character.isLetter(c)) letter = true;
            else if (Character.isDigit(c)) digit = true;
        }
        if (!letter || !digit) {
            throw new ValidationException("Password must contain letters and digits",
                    Map.of("newPassword", "passwordComplexity"));
        }
    }
}
