package com.smartlearnly.backend.payment.sepay.service;

import com.smartlearnly.backend.payment.sepay.dto.SePayPaymentInstruction;
import com.smartlearnly.backend.payment.sepay.dto.SePayPaymentInstructionRequest;

public interface SePayPaymentInstructionService {
    // Tạo thông tin chuyển khoản mà checkout trả về cho frontend.
    SePayPaymentInstruction createInstruction(SePayPaymentInstructionRequest request);
}
