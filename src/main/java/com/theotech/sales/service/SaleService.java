package com.theotech.sales.service;

import com.theotech.catalog.domain.Product;
import com.theotech.catalog.repository.ProductRepository;
import com.theotech.common.PageResponse;
import com.theotech.common.exception.NotFoundException;
import com.theotech.iam.domain.User;
import com.theotech.iam.repository.UserRepository;
import com.theotech.sales.domain.Sale;
import com.theotech.sales.dto.SaleRequest;
import com.theotech.sales.dto.SaleResponse;
import com.theotech.sales.dto.SalesHistoryResponse;
import com.theotech.sales.repository.SaleQueryRepository;
import com.theotech.sales.repository.SaleRepository;
import com.theotech.security.AppUserPrincipal;
import com.theotech.security.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Selling is one atomic step: lock the product row, check availability, add to its sold counter, write
 * the sale. If anything fails the transaction rolls back and stock is untouched.
 */
@Service
@Transactional(readOnly = true)
public class SaleService {

    private final SaleRepository sales;
    private final SaleQueryRepository saleQuery;
    private final ProductRepository products;
    private final UserRepository users;
    private final CurrentUser currentUser;

    public SaleService(SaleRepository sales, SaleQueryRepository saleQuery, ProductRepository products,
                       UserRepository users, CurrentUser currentUser) {
        this.sales = sales;
        this.saleQuery = saleQuery;
        this.products = products;
        this.users = users;
        this.currentUser = currentUser;
    }

    @Transactional
    public SaleResponse sell(SaleRequest req) {
        // SELECT ... FOR UPDATE: two people selling the last units at the same time cannot both succeed
        Product product = products.lockActiveById(req.productId())
                .orElseThrow(() -> new NotFoundException("Product", req.productId()));
        product.sell(req.quantity());   // throws INSUFFICIENT_STOCK when not enough is available
        AppUserPrincipal me = currentUser.require();
        Sale sale = sales.save(new Sale(product.getId(), product.getName(), req.quantity(), product.getPrice(), me.getId()));
        return SaleResponse.from(sale, me.getFullName(), product.getAvailableStock());
    }

    public SaleResponse get(Long id) {
        Sale s = sales.findById(id).orElseThrow(() -> new NotFoundException("Sale", id));
        return toResponses(List.of(s)).getFirst();
    }

    public SalesHistoryResponse history(Instant from, Instant to, String q, Pageable pageable) {
        Page<Sale> page = saleQuery.find(from, to, q, pageable);
        PageResponse<SaleResponse> response = new PageResponse<>(toResponses(page.getContent()), page.getNumber(),
                page.getSize(), page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
        return SalesHistoryResponse.of(response, saleQuery.totals(from, to, q));
    }

    /** The ten most recent sales, for the dashboard. */
    public List<SaleResponse> recent() {
        return toResponses(sales.findTop10ByOrderBySoldAtDescIdDesc());
    }

    /** Maps sales to rows, resolving the seller's name in one query. */
    private List<SaleResponse> toResponses(List<Sale> list) {
        Set<Long> ids = list.stream().map(Sale::getSoldBy).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> names = ids.isEmpty() ? Map.of()
                : users.findAllById(ids).stream().collect(Collectors.toMap(User::getId, User::getFullName));
        return list.stream().map(s -> SaleResponse.from(s, names.get(s.getSoldBy()), null)).toList();
    }
}
