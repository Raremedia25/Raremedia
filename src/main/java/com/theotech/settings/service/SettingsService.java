package com.theotech.settings.service;

import com.theotech.security.CurrentUser;
import com.theotech.settings.domain.Setting;
import com.theotech.settings.dto.SettingsRequest;
import com.theotech.settings.dto.SettingsResponse;
import com.theotech.settings.repository.SettingRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * The few settings the shop needs: its name/contact details and the low-stock level. Values are read
 * on demand from the tiny {@code settings} table, so what an admin saves applies immediately.
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

    private final SettingRepository repository;
    private final CurrentUser currentUser;

    public SettingsService(SettingRepository repository, CurrentUser currentUser) {
        this.repository = repository;
        this.currentUser = currentUser;
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

    /** Products with this many units or fewer count as low stock. */
    public int lowStockThreshold() {
        return Math.max(0, getInt(LOW_STOCK_THRESHOLD, DEFAULT_LOW_STOCK_THRESHOLD));
    }

    public SettingsResponse current() {
        return new SettingsResponse(getString(COMPANY_NAME, "THEO TECH LTD"), getString(COMPANY_ADDRESS, ""),
                getString(COMPANY_PHONE, ""), lowStockThreshold());
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
}
