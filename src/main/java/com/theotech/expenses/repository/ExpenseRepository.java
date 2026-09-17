package com.theotech.expenses.repository;

import com.theotech.expenses.domain.Expense;
import com.theotech.expenses.dto.ExpenseCategoryTotal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long>, JpaSpecificationExecutor<Expense> {

    @Query("select coalesce(sum(e.amount), 0) from Expense e")
    Number totalAmount();

    /** {@code from} inclusive, {@code to} exclusive (calendar days). */
    @Query("select coalesce(sum(e.amount), 0) from Expense e where e.spentOn >= :from and e.spentOn < :to")
    Number totalBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select new com.theotech.expenses.dto.ExpenseCategoryTotal(e.category, count(e), sum(e.amount)) " +
           "from Expense e where e.spentOn >= :from and e.spentOn < :to group by e.category order by sum(e.amount) desc")
    List<ExpenseCategoryTotal> totalsByCategory(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select distinct e.category from Expense e order by e.category")
    List<String> categories();
}
