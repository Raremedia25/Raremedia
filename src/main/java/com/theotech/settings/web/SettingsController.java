package com.theotech.settings.web;

import com.theotech.common.ApiResponse;
import com.theotech.settings.dto.MailSettingsRequest;
import com.theotech.settings.dto.MailSettingsResponse;
import com.theotech.settings.dto.SettingsRequest;
import com.theotech.settings.dto.SettingsResponse;
import com.theotech.settings.service.MailTestService;
import com.theotech.settings.service.SettingsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService service;
    private final MailTestService mailTest;

    public SettingsController(SettingsService service, MailTestService mailTest) {
        this.service = service;
        this.mailTest = mailTest;
    }

    /** Shop name, contact details, low-stock level: every signed-in user may read them (the sidebar shows the name). */
    @GetMapping
    public ApiResponse<SettingsResponse> get() {
        return ApiResponse.ok(service.current());
    }

    @PutMapping
    public ApiResponse<SettingsResponse> update(@Valid @RequestBody SettingsRequest request) {
        return ApiResponse.ok(service.update(request));
    }

    /** E-mail report setup: administrators only. */
    @GetMapping("/mail")
    public ApiResponse<MailSettingsResponse> mail() {
        return ApiResponse.ok(service.mailSettings());
    }

    @PutMapping("/mail")
    public ApiResponse<MailSettingsResponse> updateMail(@Valid @RequestBody MailSettingsRequest request) {
        return ApiResponse.ok(service.updateMail(request));
    }

    /** Sends a short test message to the report address with the stored SMTP account. */
    @PostMapping("/mail/test")
    public ApiResponse<Map<String, String>> test() {
        return ApiResponse.ok(Map.of("sentTo", mailTest.sendTest()));
    }
}
