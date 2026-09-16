package com.theotech.reports.service;

import com.theotech.catalog.domain.Product;
import com.theotech.catalog.repository.ProductRepository;
import com.theotech.reports.dto.SalesReportResponse;
import com.theotech.sales.dto.ProductSalesAggregate;
import com.theotech.sales.repository.SaleRepository;
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
@PreAuthorize("hasRole('ADMIN')")
@Transactional(readOnly = true)
public class ReportService {

    private final SaleRepository sales;
    private final ProductRepository products;

    public ReportService(SaleRepository sales, ProductRepository products) {
        this.sales = sales;
        this.products = products;
    }

    /** Sales per product between {@code from} (inclusive) and {@code to} (exclusive); either may be null. */
    public SalesReportResponse sales(Instant from, Instant to) {
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
        BigDecimal total = rows.stream().map(SalesReportResponse.Row::totalSales)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        return new SalesReportResponse(start, end, rows, quantity, total);
    }

    private static SalesReportResponse.Row row(ProductSalesAggregate a, Product p) {
        return new SalesReportResponse.Row(a.productId(),
                p == null ? a.productName() : p.getName(),
                p == null ? null : p.getCategory().getName(),
                a.quantity() == null ? 0 : a.quantity(),
                (a.total() == null ? BigDecimal.ZERO : a.total()).setScale(2, RoundingMode.HALF_UP),
                p == null ? null : p.getAvailableStock());
    }
}
