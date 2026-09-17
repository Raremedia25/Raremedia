package com.theotech.expenses.service;

import com.theotech.common.PageResponse;
import com.theotech.common.exception.NotFoundException;
import com.theotech.expenses.domain.Expense;
import com.theotech.expenses.dto.ExpenseRequest;
import com.theotech.expenses.dto.ExpenseResponse;
import com.theotech.expenses.dto.ExpensesPageResponse;
import com.theotech.expenses.repository.ExpenseRepository;
import com.theotech.iam.domain.User;
import com.theotech.iam.repository.UserRepository;
import com.theotech.security.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Expenses are the administrator's business (they carry money figures): every method here is admin-only. */
@Service
@PreAuthorize("hasRole('ADMIN')")
@Transactional(readOnly = true)
public class ExpenseService {

    /** DTO sort key → entity property. */
    public static final Map<String, String> SORTS = Map.of(
            "spentOn", "spentOn",
            "category", "category",
            "description", "description",
            "amount", "amount",
            "createdAt", "createdAt");

    private final ExpenseRepository expenses;
    private final UserRepository users;
    private final CurrentUser currentUser;

    public ExpenseService(ExpenseRepository expenses, UserRepository users, CurrentUser currentUser) {
        this.expenses = expenses;
        this.users = users;
        this.currentUser = currentUser;
    }

    /** {@code from} inclusive, {@code to} inclusive (calendar days); either may be null. */
    public ExpensesPageResponse list(LocalDate from, LocalDate to, String category, String q, Pageable pageable) {
        Specification<Expense> spec = specification(from, to, category, q);
        Page<Expense> page = expenses.findAll(spec, pageable);
        PageResponse<ExpenseResponse> response = new PageResponse<>(toResponses(page.getContent()), page.getNumber(),
                page.getSize(), page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
        BigDecimal total = expenses.findAll(spec).stream().map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        return ExpensesPageResponse.of(response, total);
    }

    public ExpenseResponse get(Long id) {
        return toResponses(List.of(load(id))).getFirst();
    }

    public List<String> categories() {
        return expenses.categories();
    }

    @Transactional
    public ExpenseResponse create(ExpenseRequest req) {
        Expense e = expenses.save(new Expense(req.spentOn(), clean(req.category()), req.description().trim(),
                req.amount(), blankToNull(req.note()), currentUser.idOrNull()));
        return toResponses(List.of(e)).getFirst();
    }

    @Transactional
    public ExpenseResponse update(Long id, ExpenseRequest req) {
        Expense e = load(id);
        e.update(req.spentOn(), clean(req.category()), req.description().trim(), req.amount(), blankToNull(req.note()));
        return toResponses(List.of(e)).getFirst();
    }

    @Transactional
    public void delete(Long id) {
        expenses.delete(load(id));
    }

    // ---- helpers ----------------------------------------------------------------------------

    static Specification<Expense> specification(LocalDate from, LocalDate to, String category, String q) {
        List<Specification<Expense>> specs = new ArrayList<>();
        if (from != null) specs.add((root, cq, cb) -> cb.greaterThanOrEqualTo(root.get("spentOn"), from));
        if (to != null) specs.add((root, cq, cb) -> cb.lessThanOrEqualTo(root.get("spentOn"), to));
        if (category != null && !category.isBlank()) {
            specs.add((root, cq, cb) -> cb.equal(cb.lower(root.get("category")), category.trim().toLowerCase(Locale.ROOT)));
        }
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
            specs.add((root, cq, cb) -> cb.or(
                    cb.like(cb.lower(root.get("description")), like),
                    cb.like(cb.lower(root.get("category")), like),
                    cb.like(cb.lower(cb.coalesce(root.get("note"), "")), like)));
        }
        return specs.isEmpty() ? Specification.unrestricted() : Specification.allOf(specs);
    }

    private Expense load(Long id) {
        return expenses.findById(id).orElseThrow(() -> new NotFoundException("Expense", id));
    }

    private List<ExpenseResponse> toResponses(List<Expense> list) {
        Set<Long> ids = list.stream().map(Expense::getRecordedBy).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> names = ids.isEmpty() ? Map.of()
                : users.findAllById(ids).stream().collect(Collectors.toMap(User::getId, User::getFullName));
        return list.stream().map(e -> ExpenseResponse.from(e, names.get(e.getRecordedBy()))).toList();
    }

    /** "  transport " → "Transport": one spelling per category so the filter and the report group well. */
    static String clean(String category) {
        String c = category.trim().replaceAll("\\s+", " ");
        return c.isEmpty() ? c : Character.toUpperCase(c.charAt(0)) + c.substring(1);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
