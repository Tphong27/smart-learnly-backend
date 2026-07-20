package com.smartlearnly.backend.commerce.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderExpirationScheduler {
    private static final Logger log = LoggerFactory.getLogger(OrderExpirationScheduler.class);

    private final OrderService orderService;

    @Scheduled(
            fixedDelayString = "${app.payment.order-expiration-interval:PT1M}",
            initialDelayString = "${app.payment.order-expiration-interval:PT1M}"
    )
    public void run() {
        int expiredCount = orderService.expireDueOrders();
        if (expiredCount > 0) {
            log.info("Expired {} pending checkout order(s)", expiredCount);
        }
    }
}
