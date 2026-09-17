package com.theotech.settings.dto;

/** E-mail report settings for the Settings page. The SMTP password is never returned, only whether one is stored. */
public record MailSettingsResponse(
        String reportEmail,
        boolean dailyReportEnabled,
        String dailyReportTime,
        String lastReportSentDate,
        String mailHost,
        int mailPort,
        String mailUsername,
        String mailFrom,
        boolean mailPasswordSet,
        boolean mailConfigured) {
}
