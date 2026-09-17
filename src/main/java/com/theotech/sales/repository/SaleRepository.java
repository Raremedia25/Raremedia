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

    /** All lines of one ticket, in the order they were rung up. */
    List<Sale> findByReceiptNoOrderById(Long receiptNo);

    /** Next ticket number; a sequence, so two tills never get the same one. */
    @Query(value = "select nextval('receipt_seq')", nativeQuery = true)
    long nextReceiptNo();

    @Query("select coalesce(sum(s.quantity), 0) from Sale s")
    Number totalQuantity();

    @Query("select coalesce(sum(s.total), 0) from Sale s")
    Number totalAmount();

    @Query("select coalesce(sum(s.total), 0) from Sale s where s.paid = false")
    Number unpaidAmount();

    long countByPaidFalse();

    @Query("select new com.theotech.sales.dto.ProductSalesAggregate(s.productId, s.productName, sum(s.quantity), sum(s.total), " +
           "coalesce(sum(case when s.paid = false then s.total end), 0)) " +
           "from Sale s where s.soldAt >= :from and s.soldAt < :to group by s.productId, s.productName")
    List<ProductSalesAggregate> aggregateByProduct(@Param("from") Instant from, @Param("to") Instant to);

    /** Sales still owed in a period, oldest first (for the report's "not paid" list). */
    @Query("select s from Sale s where s.paid = false and s.soldAt >= :from and s.soldAt < :to order by s.soldAt, s.id")
    List<Sale> findUnpaidBetween(@Param("from") Instant from, @Param("to") Instant to);

    long countByProductId(Long productId);
}
