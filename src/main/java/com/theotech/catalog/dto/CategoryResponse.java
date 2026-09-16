package com.theotech.catalog.dto;

import com.theotech.catalog.domain.Category;

public record CategoryResponse(Long id, String name, long productCount) {

    public static CategoryResponse from(Category c, long productCount) {
        return new CategoryResponse(c.getId(), c.getName(), productCount);
    }
}
