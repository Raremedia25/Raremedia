package com.theotech.common.web;

import com.theotech.common.exception.ValidationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Map;

/**
 * Builds a {@link Pageable} from the uniform list-endpoint parameters {@code ?page&size&sort}.
 * Sort keys are whitelisted per endpoint (DTO field name → entity property), so a client can never
 * sort by an arbitrary column or trigger a query error with an unknown property.
 */
public final class PageParams {

    public static final int MAX_SIZE = 200;

    private PageParams() {
    }

    /**
     * @param sort        {@code field} or {@code field,asc|desc}; may be null
     * @param allowed     map of allowed sort keys to entity property paths
     * @param defaultSort applied when {@code sort} is empty
     */
    public static Pageable of(Integer page, Integer size, String sort, Map<String, String> allowed, Sort defaultSort) {
        int p = page == null || page < 0 ? 0 : page;
        int s = size == null || size < 1 ? 20 : Math.min(size, MAX_SIZE);
        return PageRequest.of(p, s, parseSort(sort, allowed, defaultSort));
    }

    static Sort parseSort(String sort, Map<String, String> allowed, Sort defaultSort) {
        if (sort == null || sort.isBlank()) {
            return defaultSort;
        }
        String[] parts = sort.split(",");
        String key = parts[0].trim();
        String property = allowed.get(key);
        if (property == null) {
            throw new ValidationException("Unknown sort field", Map.of("sort", "invalid"));
        }
        Sort.Direction dir = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(dir, property);
    }
}
