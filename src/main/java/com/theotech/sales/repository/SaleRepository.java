package com.theotech.sales.repository;

import com.theotech.sales.domain.Sale;
import com.theotech.sales.dto.ProductSalesAggregate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    List<Sale> findTop10ByOrderBySoldAtDescIdDesc();

    @Query("select coalesce(sum(s.quantity), 0) from Sale s")
    Number totalQuantity();

    @Query("select coalesce(sum(s.total), 0) from Sale s")
    Number totalAmount();

    @Query("select new com.theotech.sales.dto.ProductSalesAggregate(s.productId, s.productName, sum(s.quantity), sum(s.total)) " +
           "from Sale s where s.soldAt >= :from and s.soldAt < :to group by s.productId, s.productName")
    List<ProductSalesAggregate> aggregateByProduct(@Param("from") Instant from, @Param("to") Instant to);

    long countByProductId(Long productId);
}
