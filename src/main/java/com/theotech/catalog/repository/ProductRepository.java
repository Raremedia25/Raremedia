package com.theotech.catalog.repository;

import com.theotech.catalog.domain.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    /** Lists always load the category in the same statement. */
    @Override
    @EntityGraph(attributePaths = "category")
    List<Product> findAll(Specification<Product> spec, Sort sort);

    @EntityGraph(attributePaths = "category")
    @Query("select p from Product p where p.deletedAt is null order by p.name")
    List<Product> findAllActive();

    @EntityGraph(attributePaths = "category")
    @Query("select p from Product p where p.id = :id and p.deletedAt is null")
    Optional<Product> findActiveById(@Param("id") Long id);

    /** Row lock for a sale: two sales of the last units cannot both pass the availability check. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id and p.deletedAt is null")
    Optional<Product> lockActiveById(@Param("id") Long id);

    @Query("select count(p) > 0 from Product p where lower(p.name) = lower(:name) and p.deletedAt is null " +
           "and (:excludeId is null or p.id <> :excludeId)")
    boolean existsActiveByName(@Param("name") String name, @Param("excludeId") Long excludeId);

    @Query("select count(p) from Product p where p.category.id = :categoryId and p.deletedAt is null")
    long countActiveByCategory(@Param("categoryId") Long categoryId);

    @Query("select p.category.id, count(p) from Product p where p.deletedAt is null group by p.category.id")
    List<Object[]> countPerCategory();
}
