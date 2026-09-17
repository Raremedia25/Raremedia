package com.theotech.settings.service;

import com.theotech.security.CurrentUser;
import com.theotech.settings.domain.Setting;
import com.theotech.settings.dto.MailSettings;
import com.theotech.settings.dto.MailSettingsRequest;
import com.theotech.settings.dto.MailSettingsResponse;
import com.theotech.settings.dto.SettingsRequest;
import com.theotech.settings.dto.SettingsResponse;
import com.theotech.settings.repository.SettingRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * The few settings the shop needs: its name/contact details, the low-stock level, and the e-mail report
 * setup (recipient, daily schedule, SMTP account). Values are read on demand from the tiny {@code settings}
 * table, so what an admin saves applies immediately.
 */
@Service
@Transactional(readOnly = true)
public class SettingsService {

    public static final String MAX_FAILED_LOGINS = "security.max_failed_logins";
    public static final String LOCKOUT_MINUTES = "security.lockout_minutes";
    public static final String COMPANY_NAME = "company.name";
    public static final String COMPANY_ADDRESS = "company.address";
    public static final String COMPANY_PHONE = "company.phone";
    public static final String LOW_STOCK_THRESHOLD = "stock.low_threshold";
    public static final int DEFAULT_LOW_STOCK_THRESHOLD = 5;

    public static final String REPORT_EMAIL = "report.email";
    public static final String REPORT_DAILY_ENABLED = "report.daily_enabled";
    public static final String REPORT_DAILY_TIME = "report.daily_time";
    public static final String REPORT_LAST_SENT_DATE = "report.last_sent_date";
    public static final LocalTime DEFAULT_REPORT_TIME = LocalTime.of(20, 0);
    public static final String MAIL_HOST = "mail.host";
    public static final String MAIL_PORT = "mail.port";
    public static final String MAIL_USERNAME = "mail.username";
    public static final String MAIL_PASSWORD = "mail.password";
    public static final String MAIL_FROM = "mail.from";

    private final SettingRepository repository;
    private final CurrentUser currentUser;
    private final LogoService logos;

    public SettingsService(SettingRepository repository, CurrentUser currentUser, LogoService logos) {
        this.repository = repository;
        this.currentUser = currentUser;
        this.logos = logos;
    }

    public Optional<String> get(String key) {
        return repository.findById(key).map(Setting::getValue);
    }

    public String getString(String key, String defaultValue) {
        return get(key).orElse(defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        return get(key).map(String::trim).map(v -> {
            try {
                return Integer.parseInt(v);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }).orElse(defaultValue);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return get(key).map(String::trim).map(v -> "true".equalsIgnoreCase(v)).orElse(defaultValue);
    }

    // ---- shop --------------------------------------------------------------------------------

    /** Products with this many units or fewer count as low stock. */
    public int lowStockThreshold() {
        return Math.max(0, getInt(LOW_STOCK_THRESHOLD, DEFAULT_LOW_STOCK_THRESHOLD));
    }

    public String companyName() {
        return getString(COMPANY_NAME, "THEO TECH LTD");
    }

    public SettingsResponse current() {
        return new SettingsResponse(companyName(), getString(COMPANY_ADDRESS, ""),
                getString(COMPANY_PHONE, ""), lowStockThreshold(), logos.url());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public SettingsResponse update(SettingsRequest req) {
        set(COMPANY_NAME, req.companyName().trim(), "STRING", "company", "Business name shown in the app and on reports");
        set(COMPANY_ADDRESS, req.companyAddress() == null ? "" : req.companyAddress().trim(), "STRING", "company", "Business address");
        set(COMPANY_PHONE, req.companyPhone() == null ? "" : req.companyPhone().trim(), "STRING", "company", "Business phone number");
        set(LOW_STOCK_THRESHOLD, String.valueOf(req.lowStockThreshold()), "INTEGER", "stock",
                "Products with this many units or fewer are shown as low stock");
        return current();
    }

    // ---- e-mail reports ----------------------------------------------------------------------

    public String reportEmail() {
        return getString(REPORT_EMAIL, "").trim();
    }

    public boolean dailyReportEnabled() {
        return getBoolean(REPORT_DAILY_ENABLED, false);
    }

    public LocalTime dailyReportTime() {
        try {
            return LocalTime.parse(getString(REPORT_DAILY_TIME, "20:00").trim());
        } catch (DateTimeParseException e) {
            return DEFAULT_REPORT_TIME;
        }
    }

    /** The shop date the daily report was last sent, or null. */
    public LocalDate lastReportSentDate() {
        String v = getString(REPORT_LAST_SENT_DATE, "").trim();
        if (v.isEmpty()) return null;
        try {
            return LocalDate.parse(v);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    @Transactional
    public void recordDailyReportSent(LocalDate date) {
        set(REPORT_LAST_SENT_DATE, date.toString(), "STRING", "report", "Date the daily report was last sent (prevents duplicates)");
    }

    public MailSettings mail() {
        return new MailSettings(getString(MAIL_HOST, "").trim(), getInt(MAIL_PORT, 587),
                getString(MAIL_USERNAME, "").trim(), getString(MAIL_PASSWORD, ""), getString(MAIL_FROM, "").trim());
    }

    @PreAuthorize("hasRole('ADMIN')")
    public MailSettingsResponse mailSettings() {
        MailSettings m = mail();
        LocalDate last = lastReportSentDate();
        return new MailSettingsResponse(reportEmail(), dailyReportEnabled(), dailyReportTime().toString(),
                last == null ? null : last.toString(), m.host(), m.port(), m.username(), m.from(),
                m.password() != null && !m.password().isEmpty(), m.configured());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public MailSettingsResponse updateMail(MailSettingsRequest req) {
        set(REPORT_EMAIL, trim(req.reportEmail()), "STRING", "report", "Where reports are e-mailed (one address, or several separated by commas)");
        set(REPORT_DAILY_ENABLED, String.valueOf(req.dailyReportEnabled()), "BOOLEAN", "report", "Send the day's sales report by e-mail every day");
        set(REPORT_DAILY_TIME, req.dailyReportTime().trim(), "STRING", "report", "Time of day (shop time) at which the daily report is sent");
        set(MAIL_HOST, trim(req.mailHost()), "STRING", "mail", "SMTP server, e.g. smtp.gmail.com");
        set(MAIL_PORT, String.valueOf(req.mailPort()), "INTEGER", "mail", "SMTP port (587 STARTTLS, 465 SSL)");
        set(MAIL_USERNAME, trim(req.mailUsername()), "STRING", "mail", "SMTP login (usually the sending e-mail address)");
        set(MAIL_FROM, trim(req.mailFrom()), "STRING", "mail", "Sender address shown on the e-mail (defaults to the username)");
        if (req.mailPassword() != null && !req.mailPassword().isBlank()) {
            set(MAIL_PASSWORD, req.mailPassword(), "STRING", "mail", "SMTP password or app password");
        }
        return mailSettings();
    }

    // ---- helpers -----------------------------------------------------------------------------

    private void set(String key, String value, String type, String category, String description) {
        Long by = currentUser.idOrNull();
        repository.findById(key).ifPresentOrElse(
                s -> s.update(value, by),
                () -> {
                    Setting s = new Setting(key, value, type, category, description);
                    s.update(value, by);
                    repository.save(s);
                });
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
