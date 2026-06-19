package com.smartlearnly.backend.payment.sepay;

import java.util.List;

public interface SePayTransactionClient {
    List<SePayTransactionCandidate> findTransactions(SePayTransactionQuery query);
}
