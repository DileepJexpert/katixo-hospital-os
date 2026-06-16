package com.katixo.hospital.patient;

import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class PatientCreditService {

    private final PatientCreditAccountRepository creditAccountRepository;
    private final PatientCreditTransactionRepository transactionRepository;
    private final PatientRepository patientRepository;

    public void initializeCreditAccount(Patient patient) {
        var context = TenantContext.get();

        PatientCreditAccount account = new PatientCreditAccount();
        account.setTenantId(context.getTenantId());
        account.setHospitalGroupId(Long.parseLong(context.getHospitalGroupId()));
        account.setBranchId(Long.parseLong(context.getBranchId()));
        account.setPatient(patient);
        account.setAvailableBalance(BigDecimal.ZERO);
        account.setTotalDebited(BigDecimal.ZERO);
        account.setTotalCredited(BigDecimal.ZERO);
        account.setCreditLimit(BigDecimal.ZERO);
        account.setStatus(PatientCreditAccount.CreditStatus.ACTIVE);
        account.setCreatedBy(Long.parseLong(context.getUserId()));
        account.setCreatedAt(LocalDateTime.now());
        account.setUpdatedBy(Long.parseLong(context.getUserId()));
        account.setUpdatedAt(LocalDateTime.now());

        creditAccountRepository.save(account);
        log.info("Credit account initialized for patient {}", patient.getId());
    }

    @Transactional(readOnly = true)
    public PatientCreditAccount getAccount(Long patientId) {
        var context = TenantContext.get();
        return creditAccountRepository.findByTenantIdAndBranchIdAndPatientId(
                context.getTenantId(), Long.parseLong(context.getBranchId()), patientId)
                .orElseThrow(() -> new BusinessException("CREDIT_ACCOUNT_NOT_FOUND",
                        "Credit account not found for patient: " + patientId));
    }

    /**
     * Returns the patient's credit account, creating an empty ACTIVE one on first
     * access. Registration already initializes accounts; this covers patients
     * created outside that path (e.g. seeded/migrated records).
     */
    public PatientCreditAccount getOrCreateAccount(Long patientId) {
        var context = TenantContext.get();
        return creditAccountRepository.findByTenantIdAndBranchIdAndPatientId(
                        context.getTenantId(), Long.parseLong(context.getBranchId()), patientId)
                .orElseGet(() -> {
                    Patient patient = patientRepository.findByIdAndTenantIdAndBranchId(
                                    patientId, context.getTenantId(), Long.parseLong(context.getBranchId()))
                            .orElseThrow(() -> new BusinessException("PATIENT_NOT_FOUND",
                                    "Patient not found: " + patientId));
                    initializeCreditAccount(patient);
                    return getAccount(patientId);
                });
    }

    public PatientCreditAccount deductFromBalance(Long patientId, BigDecimal amount, String billId) {
        var context = TenantContext.get();
        var account = getAccount(patientId);

        if (account.getStatus() == PatientCreditAccount.CreditStatus.BLOCKED) {
            throw new BusinessException("CREDIT_ACCOUNT_BLOCKED",
                    "Patient credit account is blocked");
        }

        if (!account.canAccommodateCharge(amount)) {
            throw new BusinessException("CREDIT_LIMIT_EXCEEDED",
                    "Patient credit limit exceeded. Available: " + account.getAvailableBalance() +
                    ", Required: " + amount);
        }

        BigDecimal newBalance = account.getAvailableBalance().subtract(amount);
        account.setAvailableBalance(newBalance);
        account.setTotalDebited(account.getTotalDebited().add(amount));
        account.setLastTransactionAt(LocalDateTime.now());
        account.setUpdatedBy(Long.parseLong(context.getUserId()));
        account.setUpdatedAt(LocalDateTime.now());

        PatientCreditAccount saved = creditAccountRepository.save(account);

        recordTransaction(patientId, PatientCreditTransaction.TransactionType.BILL_CHARGE,
                amount, newBalance, "BILL", billId,
                "Bill charge: " + billId);

        log.info("Deducted {} from patient {} credit (Bill: {}). New balance: {}",
                amount, patientId, billId, newBalance);

        return saved;
    }

    public PatientCreditAccount addCredit(Long patientId, BigDecimal amount, String paymentId) {
        var context = TenantContext.get();
        var account = getAccount(patientId);

        BigDecimal newBalance = account.getAvailableBalance().add(amount);
        account.setAvailableBalance(newBalance);
        account.setTotalCredited(account.getTotalCredited().add(amount));
        account.setLastTransactionAt(LocalDateTime.now());
        account.setUpdatedBy(Long.parseLong(context.getUserId()));
        account.setUpdatedAt(LocalDateTime.now());

        PatientCreditAccount saved = creditAccountRepository.save(account);

        recordTransaction(patientId, PatientCreditTransaction.TransactionType.PAYMENT,
                amount, newBalance, "PAYMENT", paymentId,
                "Payment received: " + paymentId);

        log.info("Added {} credit to patient {} (Payment: {}). New balance: {}",
                amount, patientId, paymentId, newBalance);

        return saved;
    }

    public PatientCreditAccount adjustBalance(Long patientId, BigDecimal amount, String reason) {
        var context = TenantContext.get();
        var account = getAccount(patientId);

        BigDecimal newBalance = account.getAvailableBalance().add(amount);
        account.setAvailableBalance(newBalance);
        if (amount.compareTo(BigDecimal.ZERO) > 0) {
            account.setTotalCredited(account.getTotalCredited().add(amount));
        } else {
            account.setTotalDebited(account.getTotalDebited().add(amount.abs()));
        }
        account.setLastTransactionAt(LocalDateTime.now());
        account.setUpdatedBy(Long.parseLong(context.getUserId()));
        account.setUpdatedAt(LocalDateTime.now());

        PatientCreditAccount saved = creditAccountRepository.save(account);

        recordTransaction(patientId, PatientCreditTransaction.TransactionType.ADJUSTMENT,
                amount.abs(), newBalance, null, null,
                "Manual adjustment: " + reason);

        log.info("Adjusted balance for patient {} by {} ({}). New balance: {}",
                patientId, amount, reason, newBalance);

        return saved;
    }

    public void reverseTransaction(Long patientId, BigDecimal amount, String sourceRef, String reason) {
        var context = TenantContext.get();
        var account = getAccount(patientId);

        BigDecimal newBalance = account.getAvailableBalance().add(amount);
        account.setAvailableBalance(newBalance);
        account.setTotalDebited(account.getTotalDebited().subtract(amount));
        account.setLastTransactionAt(LocalDateTime.now());
        account.setUpdatedBy(Long.parseLong(context.getUserId()));
        account.setUpdatedAt(LocalDateTime.now());

        creditAccountRepository.save(account);

        recordTransaction(patientId, PatientCreditTransaction.TransactionType.REVERSAL,
                amount, newBalance, "BILL", sourceRef,
                "Reversal: " + reason);

        log.info("Reversed charge of {} for patient {} ({}). New balance: {}",
                amount, patientId, reason, newBalance);
    }

    public void setCreditLimit(Long patientId, BigDecimal limit) {
        var context = TenantContext.get();
        var account = getAccount(patientId);

        account.setCreditLimit(limit);
        account.setUpdatedBy(Long.parseLong(context.getUserId()));
        account.setUpdatedAt(LocalDateTime.now());

        creditAccountRepository.save(account);
        log.info("Credit limit set to {} for patient {}", limit, patientId);
    }

    public void updateCreditStatus(Long patientId, PatientCreditAccount.CreditStatus status) {
        var context = TenantContext.get();
        var account = getAccount(patientId);

        account.setStatus(status);
        account.setUpdatedBy(Long.parseLong(context.getUserId()));
        account.setUpdatedAt(LocalDateTime.now());

        creditAccountRepository.save(account);
        log.info("Credit account status changed to {} for patient {}", status, patientId);
    }

    private void recordTransaction(Long patientId, PatientCreditTransaction.TransactionType type,
                                   BigDecimal amount, BigDecimal balanceAfter,
                                   String sourceType, String sourceRef, String description) {
        var context = TenantContext.get();

        PatientCreditTransaction transaction = new PatientCreditTransaction();
        transaction.setTenantId(context.getTenantId());
        transaction.setHospitalGroupId(Long.parseLong(context.getHospitalGroupId()));
        transaction.setBranchId(Long.parseLong(context.getBranchId()));
        transaction.setPatientId(patientId);
        transaction.setTransactionType(type);
        transaction.setAmount(amount);
        transaction.setBalanceAfter(balanceAfter);
        transaction.setSourceType(sourceType);
        transaction.setSourceRef(sourceRef);
        transaction.setDescription(description);
        transaction.setCreatedBy(Long.parseLong(context.getUserId()));
        transaction.setCreatedAt(LocalDateTime.now());

        transactionRepository.save(transaction);
    }
}
