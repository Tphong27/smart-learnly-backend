package com.smartlearnly.backend.file.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class StorageConfig {
    @Bean
    RestClient.Builder storageRestClientBuilder() {
        return RestClient.builder();
    }
}
