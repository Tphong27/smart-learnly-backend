package com.smartlearnly.backend.payment.sepay;

import com.smartlearnly.backend.admin.settings.service.SystemSettingsService;
import com.smartlearnly.backend.common.exception.BusinessException;
import com.smartlearnly.backend.common.exception.ErrorCode;
import com.smartlearnly.backend.commerce.entity.SePayOrder;
import com.smartlearnly.backend.commerce.entity.SePayOrderStatus;
import com.smartlearnly.backend.commerce.repository.SePayOrderRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SePayReconciliationService {
    private static final Logger log = LoggerFactory.getLogger(SePayReconciliationService.class);
    private static final int MAX_PENDING_ORDERS = 100;

    private final SePayProperties sePayProperties;
    private final SystemSettingsService systemSettingsService;
    private final SePayOrderRepository sePayOrderRepository;
    private final SePayTransactionClient sePayTransactionClient;
    private final SePayPaymentMatchingService paymentMatchingService;

    public ReconciliationSummary reconcile() {
        if (!hasApiToken()) {
            log.info("SePay reconciliation skipped because API token is not configured");
            return new ReconciliationSummary(0, 0, 0, 0, 0);
        }

        List<SePayOrder> pendingOrders = sePayOrderRepository.findByStatusInOrderByCreatedAtAsc(
                List.of(SePayOrderStatus.CREATED, SePayOrderStatus.WAITING_PAYMENT),
                PageRequest.of(0, MAX_PENDING_ORDERS)
        );
        int queriedOrders = 0;
        int matchedCandidates = 0;
        int queryFailures = 0;
        int candidateFailures = 0;
        for (SePayOrder sePayOrder : pendingOrders) {
            ReconciliationItemSummary itemSummary = reconcileOrder(sePayOrder);
            queriedOrders += itemSummary.queriedOrders();
            matchedCandidates += itemSummary.matchedCandidates();
            queryFailures += itemSummary.queryFailures();
            candidateFailures += itemSummary.candidateFailures();
        }
        return new ReconciliationSummary(
                pendingOrders.size(),
                queriedOrders,
                matchedCandidates,
                queryFailures,
                candidateFailures);
    }

    private ReconciliationItemSummary reconcileOrder(SePayOrder sePayOrder) {
        try {
            List<SePayTransactionCandidate> candidates = sePayTransactionClient.findTransactions(
                    SePayTransactionQuery.forPaymentCode(sePayOrder.getPaymentCode(), sePayOrder.getAmount())
            );
            int matchedCandidates = 0;
            int candidateFailures = 0;
            for (SePayTransactionCandidate candidate : candidates) {
                if (processCandidate(sePayOrder.getPaymentCode(), candidate)) {
                    matchedCandidates += 1;
                } else {
                    candidateFailures += 1;
                }
            }
            return new ReconciliationItemSummary(1, matchedCandidates, 0, candidateFailures);
        }
        catch (RuntimeException exception) {
            log.warn(
                    "SePay reconciliation query failed for paymentCode={} errorType={}",
                    sePayOrder.getPaymentCode(),
                    exception.getClass().getSimpleName()
            );
            return new ReconciliationItemSummary(0, 0, 1, 0);
        }
    }

    private boolean processCandidate(String paymentCode, SePayTransactionCandidate candidate) {
        try {
            paymentMatchingService.processReconciledTransaction(candidate);
            return true;
        }
        catch (RuntimeException exception) {
            log.warn(
                    "SePay reconciliation candidate failed for paymentCode={} errorType={}",
                    paymentCode,
                    exception.getClass().getSimpleName()
            );
            return false;
        }
    }

    public ReconciliationSummary reconcileNow() {
        if (!hasApiToken()) {
            throw new BusinessException(
                    ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "SePay transaction service is not configured");
        }
        return reconcile();
    }

    private boolean hasApiToken() {
        return systemSettingsService.resolveSePayRuntimeSettings().hasApiToken();
    }

    public record ReconciliationSummary(
            int pendingOrders,
            int queriedOrders,
            int matchedCandidates,
            int queryFailures,
            int candidateFailures) {
    }

    private record ReconciliationItemSummary(
            int queriedOrders,
            int matchedCandidates,
            int queryFailures,
            int candidateFailures) {
    }
}
