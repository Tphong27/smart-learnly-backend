package com.smartlearnly.backend.commerce.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderExpirationSchedulerTest {
    @Mock
    private OrderService orderService;

    @Test
    void runShouldDelegateToOrderService() {
        when(orderService.expireDueOrders()).thenReturn(2);
        OrderExpirationScheduler scheduler = new OrderExpirationScheduler(orderService);

        scheduler.run();

        verify(orderService).expireDueOrders();
    }
}
