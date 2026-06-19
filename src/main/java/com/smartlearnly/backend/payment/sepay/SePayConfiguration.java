package com.smartlearnly.backend.payment.sepay;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SePayProperties.class)
public class SePayConfiguration {
}
