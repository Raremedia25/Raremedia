package com.theotech.reports.service;

import com.theotech.catalog.domain.Product;
import com.theotech.catalog.dto.ProductResponse;
import com.theotech.catalog.repository.ProductRepository;
import com.theotech.config.AppProperties;
import com.theotech.reports.dto.SalesReportResponse;
import com.theotech.settings.domain.ShopLogo;
import com.theotech.settings.service.LogoService;
import com.theotech.settings.service.SettingsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/** "Download PDF" on the Reports page: the same report as on screen, with the shop's logo, as an A4 PDF. */
@Service
@Transactional(readOnly = true)
public class ReportPdfService {

    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ReportService reports;
    private final ProductRepository products;
    private final SettingsService settings;
    private final LogoService logos;
    private final AppProperties props;

    public ReportPdfService(ReportService reports, ProductRepository products, SettingsService settings,
                            LogoService logos, AppProperties props) {
        this.reports = reports;
        this.products = products;
        this.settings = settings;
        this.logos = logos;
        this.props = props;
    }

    public record Pdf(byte[] bytes, String fileName) {
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Pdf salesReport(Instant from, Instant to) {
        SalesReportResponse report = reports.build(from, to);
        int threshold = settings.lowStockThreshold();
        List<ProductResponse> low = products.findAllActive().stream()
                .filter(p -> p.getAvailableStock() <= threshold)
                .sorted(Comparator.comparingInt(Product::getAvailableStock).thenComparing(Product::getName))
                .map(p -> ProductResponse.from(p, threshold)).toList();
        ReportPdf.Shop shop = new ReportPdf.Shop(settings.companyName(),
                settings.getString(SettingsService.COMPANY_ADDRESS, ""), settings.getString(SettingsService.COMPANY_PHONE, ""),
                logos.current().map(ShopLogo::getContent).orElse(null));
        byte[] bytes = ReportPdf.render(shop, "", report, low, props.zoneId());

        String start = report.from().equals(Instant.EPOCH) ? "all" : FILE_DATE.format(report.from().atZone(props.zoneId()));
        String end = FILE_DATE.format(report.to().atZone(props.zoneId()).minusSeconds(1));
        String name = "sales-report-" + (start.equals(end) ? start : start + "_" + end) + ".pdf";
        return new Pdf(bytes, name);
    }
}
