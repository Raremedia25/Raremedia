package com.theotech.dashboard.web;

import com.theotech.common.ApiResponse;
import com.theotech.dashboard.dto.DashboardResponse;
import com.theotech.dashboard.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/api/dashboard")
    public ApiResponse<DashboardResponse> dashboard() {
        return ApiResponse.ok(service.summary());
    }
}
