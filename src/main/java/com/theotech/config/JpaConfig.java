package com.theotech.config;

import com.theotech.security.CurrentUser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Instant;
import java.util.Optional;

/**
 * JPA auditing: {@code created_by}/{@code updated_by} are filled with the authenticated user's id,
 * {@code created_at}/{@code updated_at} with UTC instants.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider", dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaConfig {

    @Bean
    public AuditorAware<Long> auditorProvider(CurrentUser currentUser) {
        return () -> Optional.ofNullable(currentUser.idOrNull());
    }

    @Bean
    public DateTimeProvider auditingDateTimeProvider() {
        return () -> Optional.of(Instant.now());
    }
}
