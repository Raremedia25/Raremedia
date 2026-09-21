package com.theotech.reports.service;

import com.theotech.catalog.domain.Product;
import com.theotech.catalog.dto.ProductResponse;
import com.theotech.catalog.repository.ProductRepository;
import com.theotech.config.AppProperties;
import com.theotech.reports.dto.SalesReportResponse;
import com.theotech.settings.domain.ShopLogo;
import com.theotech.settings.service.LogoService;
import com.theotech.settings.service.SettingsService;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/** "Download" on the Reports page: the same report as on screen, as a PDF (with the shop logo) or an Excel workbook. Admin only. */
@Service
@PreAuthorize("hasRole('ADMIN')")
@Transactional(readOnly = true)
public class ReportExportService {

    public static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ReportService reports;
    private final ProductRepository products;
    private final SettingsService settings;
    private final LogoService logos;
    private final AppProperties props;

    public ReportExportService(ReportService reports, ProductRepository products, SettingsService settings,
                               LogoService logos, AppProperties props) {
        this.reports = reports;
        this.products = products;
        this.settings = settings;
        this.logos = logos;
        this.props = props;
    }

    public record Export(byte[] bytes, String fileName, MediaType mediaType) {
    }

    public Export pdf(Instant from, Instant to) {
        SalesReportResponse report = reports.build(from, to);
        ReportPdf.Shop shop = new ReportPdf.Shop(settings.companyName(),
                settings.getString(SettingsService.COMPANY_ADDRESS, ""), settings.getString(SettingsService.COMPANY_PHONE, ""),
                logos.current().map(ShopLogo::getContent).orElse(null));
        byte[] bytes = ReportPdf.render(shop, "", report, lowStock(), props.zoneId());
        return new Export(bytes, fileName(report, "pdf"), MediaType.APPLICATION_PDF);
    }

    public Export excel(Instant from, Instant to) {
        SalesReportResponse report = reports.build(from, to);
        byte[] bytes = ReportExcel.render(settings.companyName(), "", report, lowStock(), props.zoneId());
        return new Export(bytes, fileName(report, "xlsx"), XLSX);
    }

    private List<ProductResponse> lowStock() {
        int threshold = settings.lowStockThreshold();
        return products.findAllActive().stream()
                .filter(p -> p.getAvailableStock() <= threshold)
                .sorted(Comparator.comparingInt(Product::getAvailableStock).thenComparing(Product::getName))
                .map(p -> ProductResponse.from(p, threshold)).toList();
    }

    private String fileName(SalesReportResponse report, String extension) {
        String start = report.from().equals(Instant.EPOCH) ? "all" : FILE_DATE.format(report.from().atZone(props.zoneId()));
        String end = FILE_DATE.format(report.to().atZone(props.zoneId()).minusSeconds(1));
        return "sales-report-" + (start.equals(end) ? start : start + "_" + end) + "." + extension;
    }
}
