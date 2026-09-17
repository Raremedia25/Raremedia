package com.theotech.settings.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code PUT /api/settings/mail}. A blank {@code mailPassword} keeps the stored one.
 * {@code reportEmail} accepts one address or several separated by commas.
 */
public record MailSettingsRequest(
        @Size(max = 300, message = "maxLength")
        @Pattern(regexp = "^$|^\\s*[^\\s,;@]+@[^\\s,;@]+\\.[^\\s,;@]+\\s*([,;]\\s*[^\\s,;@]+@[^\\s,;@]+\\.[^\\s,;@]+\\s*)*$", message = "email")
        String reportEmail,
        @NotNull(message = "required") Boolean dailyReportEnabled,
        @NotNull(message = "required") @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "time") String dailyReportTime,
        @Size(max = 200, message = "maxLength") String mailHost,
        @NotNull(message = "required") @Min(value = 1, message = "invalid") @Max(value = 65535, message = "invalid") Integer mailPort,
        @Size(max = 200, message = "maxLength") String mailUsername,
        @Size(max = 200, message = "maxLength") String mailPassword,
        @Size(max = 200, message = "maxLength") String mailFrom) {
}
