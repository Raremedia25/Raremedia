package com.theotech.common.web;

import com.theotech.common.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageParamsTest {

    private static final Map<String, String> ALLOWED = Map.of("name", "fullName", "createdAt", "createdAt");
    private static final Sort DEFAULT = Sort.by("fullName");

    @Test
    void defaultsWhenNothingGiven() {
        Pageable p = PageParams.of(null, null, null, ALLOWED, DEFAULT);
        assertThat(p.getPageNumber()).isZero();
        assertThat(p.getPageSize()).isEqualTo(20);
        assertThat(p.getSort()).isEqualTo(DEFAULT);
    }

    @Test
    void clampsPageAndSize() {
        Pageable p = PageParams.of(-3, 100000, "", ALLOWED, DEFAULT);
        assertThat(p.getPageNumber()).isZero();
        assertThat(p.getPageSize()).isEqualTo(PageParams.MAX_SIZE);
        assertThat(PageParams.of(2, 0, null, ALLOWED, DEFAULT).getPageSize()).isEqualTo(20);
    }

    @Test
    void mapsSortKeyToEntityPropertyWithDirection() {
        assertThat(PageParams.of(0, 10, "name,desc", ALLOWED, DEFAULT).getSort())
                .isEqualTo(Sort.by(Sort.Direction.DESC, "fullName"));
        assertThat(PageParams.of(0, 10, "createdAt", ALLOWED, DEFAULT).getSort())
                .isEqualTo(Sort.by(Sort.Direction.ASC, "createdAt"));
        assertThat(PageParams.of(0, 10, " name , DESC ", ALLOWED, DEFAULT).getSort())
                .isEqualTo(Sort.by(Sort.Direction.DESC, "fullName"));
    }

    @Test
    void rejectsUnknownSortKeys() {
        assertThatThrownBy(() -> PageParams.of(0, 10, "passwordHash", ALLOWED, DEFAULT))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).getErrors()).containsEntry("sort", "invalid"));
    }
}
