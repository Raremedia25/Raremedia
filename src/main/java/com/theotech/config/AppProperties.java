package com.theotech.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;

/** Application-level settings bound from {@code app.*} in application.yml. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Security security, String timezone) {

    public AppProperties {
        if (security == null) security = new Security(null);
        if (timezone == null || timezone.isBlank()) timezone = "Africa/Kigali";
    }

    public ZoneId zoneId() {
        return ZoneId.of(timezone);
    }

    public record Security(String rememberMeKey) {
        public Security {
            if (rememberMeKey == null || rememberMeKey.isBlank()) rememberMeKey = "theo-tech-remember-me";
        }
    }
}
