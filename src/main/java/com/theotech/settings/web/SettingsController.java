package com.theotech.settings.web;

import com.theotech.common.ApiResponse;
import com.theotech.settings.dto.SettingsRequest;
import com.theotech.settings.dto.SettingsResponse;
import com.theotech.settings.service.SettingsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService service;

    public SettingsController(SettingsService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<SettingsResponse> get() {
        return ApiResponse.ok(service.current());
    }

    @PutMapping
    public ApiResponse<SettingsResponse> update(@Valid @RequestBody SettingsRequest request) {
        return ApiResponse.ok(service.update(request));
    }
}
