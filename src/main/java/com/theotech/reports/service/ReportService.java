package com.theotech.reports.service;

import com.theotech.catalog.domain.Product;
import com.theotech.catalog.repository.ProductRepository;
import com.theotech.reports.dto.SalesReportResponse;
import com.theotech.sales.dto.ProductSalesAggregate;
import com.theotech.sales.repository.SaleRepository;
import com.theotech.sales.service.SaleService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ReportService {

    private final SaleRepository sales;
    private final ProductRepository products;
    private final SaleService saleService;

    public ReportService(SaleRepository sales, ProductRepository products, SaleService saleService) {
        this.sales = sales;
        this.products = products;
        this.saleService = saleService;
    }

    /** The report for the screen: administrators only. */
    @PreAuthorize("hasRole('ADMIN')")
    public SalesReportResponse sales(Instant from, Instant to) {
        return build(from, to);
    }

    /**
     * Sales per product between {@code from} (inclusive) and {@code to} (exclusive); either may be null.
     * Unguarded so the scheduled e-mail (no signed-in user) can use it; callers facing the API go through {@link #sales}.
     */
    public SalesReportResponse build(Instant from, Instant to) {
        Instant start = from == null ? Instant.EPOCH : from;
        Instant end = to == null ? Instant.now().plusSeconds(86_400) : to;

        Map<Long, Product> byId = products.findAllActive().stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<SalesReportResponse.Row> rows = sales.aggregateByProduct(start, end).stream()
                .map(a -> row(a, byId.get(a.productId())))
                .sorted(Comparator.comparing(SalesReportResponse.Row::totalSales).reversed()
                        .thenComparing(SalesReportResponse.Row::productName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        long quantity = rows.stream().mapToLong(SalesReportResponse.Row::quantitySold).sum();
        BigDecimal total = sum(rows.stream().map(SalesReportResponse.Row::totalSales));
        BigDecimal unpaid = sum(rows.stream().map(SalesReportResponse.Row::unpaidSales));
        return new SalesReportResponse(start, end, rows, quantity, total, total.subtract(unpaid), unpaid,
                saleService.toResponses(sales.findUnpaidBetween(start, end)));
    }

    private static SalesReportResponse.Row row(ProductSalesAggregate a, Product p) {
        return new SalesReportResponse.Row(a.productId(),
                p == null ? a.productName() : p.getName(),
                p == null ? null : p.getCategory().getName(),
                a.quantity() == null ? 0 : a.quantity(),
                money(a.total()),
                money(a.unpaidTotal()),
                p == null ? null : p.getAvailableStock());
    }

    private static BigDecimal money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal sum(java.util.stream.Stream<BigDecimal> values) {
        return values.reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }
}
