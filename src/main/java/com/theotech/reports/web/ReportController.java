package com.theotech.reports.web;

import com.theotech.common.ApiResponse;
import com.theotech.reports.dto.SalesReportResponse;
import com.theotech.reports.service.ReportMailer;
import com.theotech.reports.service.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService service;
    private final ReportMailer mailer;

    public ReportController(ReportService service, ReportMailer mailer) {
        this.service = service;
        this.mailer = mailer;
    }

    /** {@code GET /api/reports/sales?from=...&to=...} — ISO instants, both optional, {@code to} exclusive. */
    @GetMapping("/sales")
    public ApiResponse<SalesReportResponse> sales(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ApiResponse.ok(service.sales(from, to));
    }

    /** E-mails the same report to the address in Settings. Same parameters. */
    @PostMapping("/sales/email")
    public ApiResponse<Map<String, String>> email(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ApiResponse.ok(Map.of("sentTo", mailer.sendRequested(from, to)));
    }
}
