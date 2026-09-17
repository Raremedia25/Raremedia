package com.theotech.reports.web;

import com.theotech.common.ApiResponse;
import com.theotech.reports.dto.SalesReportResponse;
import com.theotech.reports.service.ReportMailer;
import com.theotech.reports.service.ReportPdfService;
import com.theotech.reports.service.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    private final ReportPdfService pdf;

    public ReportController(ReportService service, ReportMailer mailer, ReportPdfService pdf) {
        this.service = service;
        this.mailer = mailer;
        this.pdf = pdf;
    }

    /** {@code GET /api/reports/sales?from=...&to=...} — ISO instants, both optional, {@code to} exclusive. */
    @GetMapping("/sales")
    public ApiResponse<SalesReportResponse> sales(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ApiResponse.ok(service.sales(from, to));
    }

    /** The same report as an A4 PDF download (with the shop logo). Same parameters. */
    @GetMapping(value = "/sales/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> salesPdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        ReportPdfService.Pdf file = pdf.salesReport(from, to);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .body(file.bytes());
    }

    /** E-mails the same report to the address in Settings. Same parameters. */
    @PostMapping("/sales/email")
    public ApiResponse<Map<String, String>> email(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ApiResponse.ok(Map.of("sentTo", mailer.sendRequested(from, to)));
    }
}
