package com.theotech.catalog.repository;

import com.theotech.catalog.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    @Query("select c from Category c where c.deletedAt is null order by c.name")
    List<Category> findAllActive();

    @Query("select c from Category c where c.id = :id and c.deletedAt is null")
    Optional<Category> findActiveById(@Param("id") Long id);

    @Query("select c from Category c where lower(c.name) = lower(:name) and c.deletedAt is null")
    Optional<Category> findActiveByName(@Param("name") String name);
}
