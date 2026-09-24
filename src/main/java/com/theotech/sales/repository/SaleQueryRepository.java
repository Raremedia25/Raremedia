package com.theotech.sales.repository;

import com.theotech.sales.domain.Sale;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sales history with optional date range, paid/unpaid filter, seller filter and product-name search, plus the
 * totals of the same filter so the page footer ("20 items, RWF 300,000, RWF 40,000 not paid") always matches the rows.
 * The seller is joined only to sort by name ({@code soldByName}); the rows themselves carry {@code soldBy}.
 */
@Repository
public class SaleQueryRepository {

    /** DTO sort key → HQL expression; the only way a client can influence ORDER BY. */
    public static final Map<String, String> SORTS = Map.of(
            "soldAt", "s.soldAt",
            "productName", "s.productName",
            "quantity", "s.quantity",
            "unitPrice", "s.unitPrice",
            "total", "s.total",
            "paid", "s.paid",
            "customerName", "s.customerName",
            "soldByName", "u.fullName");

    public record Totals(long quantity, BigDecimal amount, long unpaidCount, BigDecimal unpaidAmount) {
    }

    @PersistenceContext
    private EntityManager em;

    public Page<Sale> find(Instant from, Instant to, String q, Boolean paid, Long soldBy, Pageable pageable) {
        Filter f = filter(from, to, q, paid, soldBy);
        TypedQuery<Long> count = em.createQuery("select count(s) from Sale s" + f.where, Long.class);
        f.params.forEach(count::setParameter);
        long total = count.getSingleResult();

        TypedQuery<Sale> select = em.createQuery("select s from Sale s left join User u on u.id = s.soldBy" + f.where
                + orderBy(pageable.getSort()), Sale.class);
        f.params.forEach(select::setParameter);
        select.setFirstResult((int) pageable.getOffset());
        select.setMaxResults(pageable.getPageSize());
        return new PageImpl<>(select.getResultList(), pageable, total);
    }

    public Totals totals(Instant from, Instant to, String q, Boolean paid, Long soldBy) {
        Filter f = filter(from, to, q, paid, soldBy);
        TypedQuery<Object[]> query = em.createQuery(
                "select coalesce(sum(s.quantity), 0), coalesce(sum(s.total), 0), " +
                "coalesce(sum(case when s.paid = false then 1 end), 0), coalesce(sum(case when s.paid = false then s.total end), 0) " +
                "from Sale s" + f.where, Object[].class);
        f.params.forEach(query::setParameter);
        Object[] row = query.getSingleResult();
        return new Totals(((Number) row[0]).longValue(), toDecimal(row[1]), ((Number) row[2]).longValue(), toDecimal(row[3]));
    }

    private record Filter(String where, Map<String, Object> params) {
    }

    private static Filter filter(Instant from, Instant to, String q, Boolean paid, Long soldBy) {
        List<String> clauses = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        if (from != null) {
            clauses.add("s.soldAt >= :from");
            params.put("from", from);
        }
        if (to != null) {
            clauses.add("s.soldAt < :to");
            params.put("to", to);
        }
        if (paid != null) {
            clauses.add("s.paid = :paid");
            params.put("paid", paid);
        }
        if (soldBy != null) {
            clauses.add("s.soldBy = :soldBy");
            params.put("soldBy", soldBy);
        }
        if (q != null && !q.isBlank()) {
            clauses.add("(lower(s.productName) like :q or lower(coalesce(s.customerName, '')) like :q)");
            params.put("q", "%" + q.trim().toLowerCase(Locale.ROOT) + "%");
        }
        return new Filter(clauses.isEmpty() ? "" : " where " + String.join(" and ", clauses), params);
    }

    private static String orderBy(Sort sort) {
        List<String> parts = new ArrayList<>();
        for (Sort.Order o : sort) {
            String expr = SORTS.containsValue(o.getProperty()) ? o.getProperty() : SORTS.get(o.getProperty());
            if (expr == null) throw new IllegalArgumentException("Unsupported sort: " + o.getProperty());
            parts.add(expr + (o.isDescending() ? " desc" : " asc"));
        }
        if (parts.isEmpty()) parts.add("s.soldAt desc");
        parts.add("s.id desc");
        return " order by " + String.join(", ", parts);
    }

    private static BigDecimal toDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO.setScale(2);
        if (v instanceof BigDecimal d) return d.setScale(2, java.math.RoundingMode.HALF_UP);
        return new BigDecimal(v.toString()).setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
