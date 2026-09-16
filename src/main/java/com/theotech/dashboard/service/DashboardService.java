package com.theotech.dashboard.service;

import com.theotech.catalog.domain.Product;
import com.theotech.catalog.dto.ProductResponse;
import com.theotech.catalog.repository.ProductRepository;
import com.theotech.dashboard.dto.DashboardResponse;
import com.theotech.sales.repository.SaleRepository;
import com.theotech.sales.service.SaleService;
import com.theotech.settings.service.SettingsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final ProductRepository products;
    private final SaleRepository sales;
    private final SaleService saleService;
    private final SettingsService settings;

    public DashboardService(ProductRepository products, SaleRepository sales, SaleService saleService, SettingsService settings) {
        this.products = products;
        this.sales = sales;
        this.saleService = saleService;
        this.settings = settings;
    }

    public DashboardResponse summary() {
        int threshold = settings.lowStockThreshold();
        List<Product> all = products.findAllActive();

        long inStock = all.stream().mapToLong(Product::getAvailableStock).filter(n -> n > 0).sum();
        List<ProductResponse> low = all.stream()
                .filter(p -> p.getAvailableStock() > 0 && p.getAvailableStock() <= threshold)
                .sorted(Comparator.comparingInt(Product::getAvailableStock).thenComparing(Product::getName))
                .map(p -> ProductResponse.from(p, threshold)).toList();
        List<ProductResponse> out = all.stream()
                .filter(p -> p.getAvailableStock() <= 0)
                .sorted(Comparator.comparing(Product::getName))
                .map(p -> ProductResponse.from(p, threshold)).toList();

        BigDecimal totalSales = new BigDecimal(sales.totalAmount().toString()).setScale(2, RoundingMode.HALF_UP);
        return new DashboardResponse(all.size(), inStock, sales.totalQuantity().longValue(), totalSales,
                threshold, low, out, saleService.recent());
    }
}
