package com.katixo.hospital.expense;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    Optional<Expense> findByIdAndTenantIdAndBranchId(Long id, String tenantId, Long branchId);

    List<Expense> findByTenantIdAndBranchIdAndExpenseDateBetweenOrderByExpenseDateDescIdDesc(
            String tenantId, Long branchId, LocalDate from, LocalDate to);

    List<Expense> findByTenantIdAndBranchIdAndApprovalStatusOrderByExpenseDateDescIdDesc(
            String tenantId, Long branchId, Expense.ApprovalStatus approvalStatus);

    @Query(value = "SELECT nextval('expense_seq')", nativeQuery = true)
    long nextExpenseSequence();
}
