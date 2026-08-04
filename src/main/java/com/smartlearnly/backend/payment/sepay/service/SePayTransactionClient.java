package com.smartlearnly.backend.payment.sepay.service;

import com.smartlearnly.backend.payment.sepay.dto.SePayTransactionCandidate;
import com.smartlearnly.backend.payment.sepay.dto.SePayTransactionQuery;

import java.util.List;

public interface SePayTransactionClient {
    // Tìm các giao dịch ngân hàng phù hợp với điều kiện đối soát.
    List<SePayTransactionCandidate> findTransactions(SePayTransactionQuery query);
}
