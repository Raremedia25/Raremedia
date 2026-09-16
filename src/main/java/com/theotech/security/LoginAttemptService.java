package com.theotech.security;

import com.theotech.iam.domain.User;
import com.theotech.iam.repository.UserRepository;
import com.theotech.settings.service.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Failed-login bookkeeping and automatic lockout (5 wrong passwords → 15 minutes, both from {@code settings}).
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private final UserRepository userRepository;
    private final SettingsService settings;

    public LoginAttemptService(UserRepository userRepository, SettingsService settings) {
        this.userRepository = userRepository;
        this.settings = settings;
    }

    /** Records a bad-password attempt. Returns true if this attempt locked the account. */
    @Transactional
    public boolean onBadCredentials(String username) {
        Optional<User> found = userRepository.findActiveByUsername(username == null ? "" : username.trim());
        if (found.isEmpty()) {
            log.info("Failed login for unknown username '{}'", username);
            return false;
        }
        User user = found.get();
        int max = Math.max(1, settings.getInt(SettingsService.MAX_FAILED_LOGINS, 5));
        int minutes = Math.max(1, settings.getInt(SettingsService.LOCKOUT_MINUTES, 15));
        boolean lockedNow = user.registerFailedLogin(max, Instant.now().plus(minutes, ChronoUnit.MINUTES));
        if (lockedNow) {
            log.warn("Account '{}' locked for {} minutes after {} failed logins", user.getUsername(), minutes, max);
        } else {
            log.info("Failed login for '{}' (attempt {} of {})", user.getUsername(), user.getFailedLoginAttempts(), max);
        }
        return lockedNow;
    }

    /** A login attempt rejected for a reason other than the password (locked, disabled). */
    public void onRejected(String username, String reason) {
        log.info("Login rejected for '{}': {}", username, reason);
    }

    @Transactional
    public void onSuccess(AppUserPrincipal principal) {
        userRepository.findById(principal.getId()).ifPresent(u -> u.registerSuccessfulLogin(Instant.now()));
    }

    public void onLogout(AppUserPrincipal principal) {
        log.info("'{}' logged out", principal.getUsername());
    }
}
