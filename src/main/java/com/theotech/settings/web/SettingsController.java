package com.theotech.settings.web;

import com.theotech.common.ApiResponse;
import com.theotech.settings.domain.ShopLogo;
import com.theotech.settings.dto.MailSettingsRequest;
import com.theotech.settings.dto.MailSettingsResponse;
import com.theotech.settings.dto.SettingsRequest;
import com.theotech.settings.dto.SettingsResponse;
import com.theotech.settings.service.LogoService;
import com.theotech.settings.service.MailTestService;
import com.theotech.settings.service.SettingsService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService service;
    private final MailTestService mailTest;
    private final LogoService logos;

    public SettingsController(SettingsService service, MailTestService mailTest, LogoService logos) {
        this.service = service;
        this.mailTest = mailTest;
        this.logos = logos;
    }

    /** Shop name, contact details, low-stock level, logo URL: every signed-in user may read them (the sidebar shows them). */
    @GetMapping
    public ApiResponse<SettingsResponse> get() {
        return ApiResponse.ok(service.current());
    }

    @PutMapping
    public ApiResponse<SettingsResponse> update(@Valid @RequestBody SettingsRequest request) {
        return ApiResponse.ok(service.update(request));
    }

    // ---- logo (GET is public so the login page can show it) ---------------------------------

    @GetMapping("/logo")
    public ResponseEntity<byte[]> logo() {
        ShopLogo logo = logos.require();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(logo.getContentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(logo.getContent());
    }

    /** Multipart field {@code file}: JPEG, PNG or GIF, max 1 MB. Answers with the new settings (incl. {@code logoUrl}). */
    @PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<SettingsResponse> uploadLogo(@RequestParam(value = "file", required = false) MultipartFile file) {
        logos.upload(file);
        return ApiResponse.ok(service.current());
    }

    @DeleteMapping("/logo")
    public ApiResponse<SettingsResponse> deleteLogo() {
        logos.delete();
        return ApiResponse.ok(service.current());
    }

    // ---- e-mail reports ------------------------------------------------------------------------

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
