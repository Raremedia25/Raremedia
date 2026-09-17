package com.theotech.dashboard.service;

import com.theotech.catalog.domain.Product;
import com.theotech.catalog.dto.ProductResponse;
import com.theotech.catalog.repository.ProductRepository;
import com.theotech.dashboard.dto.DashboardResponse;
import com.theotech.expenses.repository.ExpenseRepository;
import com.theotech.sales.repository.SaleRepository;
import com.theotech.sales.service.SaleService;
import com.theotech.security.AppUserPrincipal;
import com.theotech.security.CurrentUser;
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
    private final ExpenseRepository expenses;
    private final SaleService saleService;
    private final SettingsService settings;
    private final CurrentUser currentUser;

    public DashboardService(ProductRepository products, SaleRepository sales, ExpenseRepository expenses,
                            SaleService saleService, SettingsService settings, CurrentUser currentUser) {
        this.products = products;
        this.sales = sales;
        this.expenses = expenses;
        this.saleService = saleService;
        this.settings = settings;
        this.currentUser = currentUser;
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

        AppUserPrincipal me = currentUser.principalOrNull();
        BigDecimal spent = me != null && me.hasRole("ADMIN") ? money(expenses.totalAmount()) : null;

        return new DashboardResponse(all.size(), inStock, sales.totalQuantity().longValue(), money(sales.totalAmount()),
                sales.countByPaidFalse(), money(sales.unpaidAmount()), spent,
                threshold, low, out, saleService.recent());
    }

    private static BigDecimal money(Number n) {
        return new BigDecimal(n.toString()).setScale(2, RoundingMode.HALF_UP);
    }
}
