package com.theotech.reports.service;

import com.theotech.catalog.domain.Product;
import com.theotech.catalog.dto.ProductResponse;
import com.theotech.catalog.repository.ProductRepository;
import com.theotech.common.exception.ValidationException;
import com.theotech.common.mail.EmailSender;
import com.theotech.config.AppProperties;
import com.theotech.reports.dto.SalesReportResponse;
import com.theotech.settings.service.SettingsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * E-mails the sales report (per-product table, paid / not paid, who still owes, low stock) to the address
 * in Settings. Used by the "E-mail this report" button and by {@link DailyReportScheduler}.
 */
@Service
@Transactional(readOnly = true)
public class ReportMailer {

    private final ReportService reports;
    private final ProductRepository products;
    private final SettingsService settings;
    private final EmailSender email;
    private final AppProperties props;

    public ReportMailer(ReportService reports, ProductRepository products, SettingsService settings,
                        EmailSender email, AppProperties props) {
        this.reports = reports;
        this.products = products;
        this.settings = settings;
        this.email = email;
        this.props = props;
    }

    /** "E-mail this report" on the Reports page: administrators only. @return the address it went to */
    @PreAuthorize("hasRole('ADMIN')")
    public String sendRequested(Instant from, Instant to) {
        return send(from, to, "requested from the Reports page");
    }

    /** Unguarded: the scheduler has no signed-in user. */
    public String send(Instant from, Instant to, String periodLabel) {
        String to_ = settings.reportEmail();
        if (to_.isEmpty()) {
            throw new ValidationException("REPORT_EMAIL_MISSING", "Enter the report e-mail address in Settings first");
        }
        String company = settings.companyName();
        SalesReportResponse report = reports.build(from, to);
        int threshold = settings.lowStockThreshold();
        List<ProductResponse> low = products.findAllActive().stream()
                .filter(p -> p.getAvailableStock() <= threshold)
                .sorted(Comparator.comparingInt(Product::getAvailableStock).thenComparing(Product::getName))
                .map(p -> ProductResponse.from(p, threshold)).toList();

        String subject = company + " - sales report " + ReportHtml.range(report.from(), report.to(), props.zoneId())
                + " - " + ReportHtml.money(report.totalSales())
                + (report.totalUnpaid().signum() > 0 ? " (" + ReportHtml.money(report.totalUnpaid()) + " not paid)" : "");
        email.send(to_, subject, ReportHtml.render(company, periodLabel, report, low, props.zoneId()));
        return to_;
    }
}
