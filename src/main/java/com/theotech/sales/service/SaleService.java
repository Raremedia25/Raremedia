package com.theotech.sales.service;

import com.theotech.catalog.domain.Product;
import com.theotech.catalog.repository.ProductRepository;
import com.theotech.common.PageResponse;
import com.theotech.common.exception.InsufficientStockException;
import com.theotech.common.exception.NotFoundException;
import com.theotech.common.exception.ValidationException;
import com.theotech.iam.domain.User;
import com.theotech.iam.repository.UserRepository;
import com.theotech.sales.domain.Sale;
import com.theotech.sales.dto.ReceiptResponse;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Selling is one atomic step: lock the product rows (in id order, so two tills never deadlock), check
 * availability, add to each sold counter, write one line per product under one receipt number. If anything
 * fails the transaction rolls back and no stock moves.
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
    public ReceiptResponse sell(SaleRequest req) {
        String customer = blankToNull(req.customerName());
        if (!req.isPaid() && customer == null) {
            throw new ValidationException("The customer's name is needed for a sale that is not paid",
                    Map.of("customerName", "required"));
        }
        // merge repeated products, keep first-seen order for the ticket
        Map<Long, Integer> wanted = new LinkedHashMap<>();
        for (SaleRequest.Item item : req.lines()) {
            if (item.productId() == null) {
                throw new ValidationException("Product is required", Map.of("productId", "required"));
            }
            if (item.quantity() == null || item.quantity() < 1) {
                throw new ValidationException("Quantity must be at least 1", Map.of("quantity", "positive"));
            }
            wanted.merge(item.productId(), item.quantity(), Integer::sum);
        }
        if (wanted.isEmpty()) {
            throw new ValidationException("Nothing to sell", Map.of("items", "required"));
        }

        // SELECT ... FOR UPDATE in ascending product id: two people selling the last units at the same time cannot both succeed
        Map<Long, Product> locked = new LinkedHashMap<>();
        for (Long id : wanted.keySet().stream().sorted().toList()) {
            locked.put(id, products.lockActiveById(id).orElseThrow(() -> new NotFoundException("Product", id)));
        }
        // check every line before touching any counter: the whole ticket is refused if one item is short
        for (Map.Entry<Long, Integer> e : wanted.entrySet()) {
            Product product = locked.get(e.getKey());
            if (e.getValue() > product.getAvailableStock()) {
                throw new InsufficientStockException(product.getId(), product.getAvailableStock(), e.getValue());
            }
        }
        AppUserPrincipal me = currentUser.require();
        long receiptNo = sales.nextReceiptNo();
        Instant now = Instant.now();
        List<Sale> lines = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : wanted.entrySet()) {
            Product product = locked.get(e.getKey());
            product.sell(e.getValue());
            lines.add(new Sale(receiptNo, now, product.getId(), product.getName(), e.getValue(), product.getPrice(),
                    me.getId(), req.isPaid(), customer));
        }
        List<Sale> saved = sales.saveAll(lines);
        List<SaleResponse> rows = saved.stream()
                .map(s -> SaleResponse.from(s, me.getFullName(), locked.get(s.getProductId()).getAvailableStock()))
                .toList();
        return ReceiptResponse.of(rows);
    }

    /** Money received for a credit sale (or the reverse when it was ticked by mistake): the whole ticket moves together. */
    @Transactional
    public ReceiptResponse setReceiptPaid(long receiptNo, boolean paid) {
        List<Sale> lines = sales.findByReceiptNoOrderById(receiptNo);
        if (lines.isEmpty()) throw new NotFoundException("Receipt", Sale.formatReceiptNo(receiptNo));
        lines.forEach(s -> s.setPaid(paid));
        return ReceiptResponse.of(toResponses(lines));
    }

    /** Same, addressed by one of its lines (the sales-history row). */
    @Transactional
    public ReceiptResponse setPaid(Long saleId, boolean paid) {
        Sale s = sales.findById(saleId).orElseThrow(() -> new NotFoundException("Sale", saleId));
        return setReceiptPaid(s.getReceiptNo(), paid);
    }

    public SaleResponse get(Long id) {
        Sale s = sales.findById(id).orElseThrow(() -> new NotFoundException("Sale", id));
        return toResponses(List.of(s)).getFirst();
    }

    public ReceiptResponse receipt(long receiptNo) {
        List<Sale> lines = sales.findByReceiptNoOrderById(receiptNo);
        if (lines.isEmpty()) throw new NotFoundException("Receipt", Sale.formatReceiptNo(receiptNo));
        return ReceiptResponse.of(toResponses(lines));
    }

    public SalesHistoryResponse history(Instant from, Instant to, String q, Boolean paid, Pageable pageable) {
        Page<Sale> page = saleQuery.find(from, to, q, paid, pageable);
        PageResponse<SaleResponse> response = new PageResponse<>(toResponses(page.getContent()), page.getNumber(),
                page.getSize(), page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
        return SalesHistoryResponse.of(response, saleQuery.totals(from, to, q, paid));
    }

    /** The ten most recent sale lines, for the dashboard. */
    public List<SaleResponse> recent() {
        return toResponses(sales.findTop10ByOrderBySoldAtDescIdDesc());
    }

    /** Maps sale lines to rows, resolving the seller's name in one query. */
    public List<SaleResponse> toResponses(List<Sale> list) {
        Set<Long> ids = list.stream().map(Sale::getSoldBy).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> names = ids.isEmpty() ? Map.of()
                : users.findAllById(ids).stream().collect(Collectors.toMap(User::getId, User::getFullName));
        return list.stream().map(s -> SaleResponse.from(s, names.get(s.getSoldBy()), null)).toList();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
