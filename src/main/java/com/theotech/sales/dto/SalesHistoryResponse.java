package com.theotech.sales.dto;

import com.theotech.common.PageResponse;
import com.theotech.sales.repository.SaleQueryRepository;

import java.math.BigDecimal;
import java.util.List;

/** A page of sales plus the totals of the whole filtered set (not just the page). Same shape as PageResponse. */
public record SalesHistoryResponse(
        List<SaleResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        long totalQuantity,
        BigDecimal totalAmount) {

    public static SalesHistoryResponse of(PageResponse<SaleResponse> p, SaleQueryRepository.Totals t) {
        return new SalesHistoryResponse(p.content(), p.page(), p.size(), p.totalElements(), p.totalPages(),
                p.first(), p.last(), t.quantity(), t.amount());
    }
}
