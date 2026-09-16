package com.theotech.catalog.service;

import com.theotech.catalog.domain.Category;
import com.theotech.catalog.dto.CategoryRequest;
import com.theotech.catalog.dto.CategoryResponse;
import com.theotech.catalog.repository.CategoryRepository;
import com.theotech.catalog.repository.ProductRepository;
import com.theotech.common.exception.ConflictException;
import com.theotech.common.exception.NotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Categories are just names: unique while in use, and not deletable while a product still uses them. */
@Service
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categories;
    private final ProductRepository products;

    public CategoryService(CategoryRepository categories, ProductRepository products) {
        this.categories = categories;
        this.products = products;
    }

    public List<CategoryResponse> list() {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : products.countPerCategory()) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return categories.findAllActive().stream()
                .map(c -> CategoryResponse.from(c, counts.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public CategoryResponse create(CategoryRequest req) {
        String name = req.name().trim();
        if (categories.findActiveByName(name).isPresent()) {
            throw new ConflictException("DUPLICATE_CATEGORY", "Category already exists: " + name);
        }
        return CategoryResponse.from(categories.save(new Category(name)), 0);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void delete(Long id) {
        Category c = categories.findActiveById(id).orElseThrow(() -> new NotFoundException("Category", id));
        long inUse = products.countActiveByCategory(id);
        if (inUse > 0) {
            throw new ConflictException("CATEGORY_IN_USE", "Category has " + inUse + " product(s)");
        }
        c.markDeleted();
    }
}
