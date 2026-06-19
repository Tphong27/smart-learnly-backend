package com.smartlearnly.backend.payment.sepay;

public interface SePayPaymentInstructionService {
    SePayPaymentInstruction createInstruction(SePayPaymentInstructionRequest request);
}
