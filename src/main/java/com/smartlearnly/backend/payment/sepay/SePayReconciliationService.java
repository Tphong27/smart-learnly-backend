package com.smartlearnly.backend.payment.sepay;

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
    private final SePayOrderRepository sePayOrderRepository;
    private final SePayTransactionClient sePayTransactionClient;
    private final SePayPaymentMatchingService paymentMatchingService;

    public void reconcile() {
        if (!hasApiToken()) {
            log.info("SePay reconciliation skipped because API token is not configured");
            return;
        }

        List<SePayOrder> pendingOrders = sePayOrderRepository.findByStatusInOrderByCreatedAtAsc(
                List.of(SePayOrderStatus.CREATED, SePayOrderStatus.WAITING_PAYMENT),
                PageRequest.of(0, MAX_PENDING_ORDERS)
        );
        log.info("SePay reconciliation scanning pendingOrders={}", pendingOrders.size());
        for (SePayOrder sePayOrder : pendingOrders) {
            reconcileOrder(sePayOrder);
        }
    }

    private void reconcileOrder(SePayOrder sePayOrder) {
        try {
            List<SePayTransactionCandidate> candidates = sePayTransactionClient.findTransactions(
                    SePayTransactionQuery.forPaymentCode(sePayOrder.getPaymentCode(), sePayOrder.getAmount())
            );
            log.info(
                    "SePay reconciliation fetched candidates={} for paymentCode={}",
                    candidates.size(),
                    sePayOrder.getPaymentCode()
            );
            for (SePayTransactionCandidate candidate : candidates) {
                processCandidate(sePayOrder.getPaymentCode(), candidate);
            }
        }
        catch (RuntimeException exception) {
            log.warn(
                    "SePay reconciliation query failed for paymentCode={} errorType={}",
                    sePayOrder.getPaymentCode(),
                    exception.getClass().getSimpleName()
            );
        }
    }

    private void processCandidate(String paymentCode, SePayTransactionCandidate candidate) {
        try {
            paymentMatchingService.processReconciledTransaction(candidate);
        }
        catch (RuntimeException exception) {
            log.warn(
                    "SePay reconciliation candidate failed for paymentCode={} errorType={}",
                    paymentCode,
                    exception.getClass().getSimpleName()
            );
        }
    }

    private boolean hasApiToken() {
        return sePayProperties.getApiToken() != null && !sePayProperties.getApiToken().isBlank();
    }
}
