package com.theotech.sales.web;

import com.theotech.common.ApiResponse;
import com.theotech.common.web.PageParams;
import com.theotech.sales.dto.PaidRequest;
import com.theotech.sales.dto.ReceiptResponse;
import com.theotech.sales.dto.SaleRequest;
import com.theotech.sales.dto.SaleResponse;
import com.theotech.sales.dto.SalesHistoryResponse;
import com.theotech.sales.repository.SaleQueryRepository;
import com.theotech.sales.service.SaleService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/sales")
public class SaleController {

    private final SaleService service;

    public SaleController(SaleService service) {
        this.service = service;
    }

    /**
     * Sales history (one row per line sold): {@code ?from&to} (ISO instants, {@code to} exclusive), {@code ?q} product
     * or customer name, {@code ?paid=true|false}, {@code ?soldBy=<user id>} (one seller), paging and sort
     * ({@code soldByName} sorts by the seller's name).
     */
    @GetMapping
    public ApiResponse<SalesHistoryResponse> history(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean paid,
            @RequestParam(required = false) Long soldBy,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        var pageable = PageParams.of(page, size, sort, SaleQueryRepository.SORTS, Sort.by(Sort.Direction.DESC, "soldAt"));
        return ApiResponse.ok(service.history(from, to, q, paid, soldBy, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<SaleResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    /** One ticket with all its lines; {@code no} accepts the printed form ({@code 000123}) or the plain number. */
    @GetMapping("/receipt/{no}")
    public ApiResponse<ReceiptResponse> receipt(@PathVariable String no) {
        return ApiResponse.ok(service.receipt(Long.parseLong(no.trim())));
    }

    /** Sells the cart ({@code items}) or a single {@code productId}/{@code quantity}; answers with the receipt. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReceiptResponse> sell(@Valid @RequestBody SaleRequest request) {
        return ApiResponse.ok(service.sell(request));
    }

    /** Marks the whole ticket this line belongs to as paid / not paid. */
    @PostMapping("/{id}/paid")
    public ApiResponse<ReceiptResponse> setPaid(@PathVariable Long id, @Valid @RequestBody PaidRequest request) {
        return ApiResponse.ok(service.setPaid(id, request.paid()));
    }

    @PostMapping("/receipt/{no}/paid")
    public ApiResponse<ReceiptResponse> setReceiptPaid(@PathVariable String no, @Valid @RequestBody PaidRequest request) {
        return ApiResponse.ok(service.setReceiptPaid(Long.parseLong(no.trim()), request.paid()));
    }
}
