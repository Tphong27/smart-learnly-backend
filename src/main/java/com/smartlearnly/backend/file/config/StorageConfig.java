package com.smartlearnly.backend.file.config;

import com.smartlearnly.backend.file.service.CloudflareR2StorageClient;
import com.smartlearnly.backend.file.service.FileStorageService;
import com.smartlearnly.backend.file.service.SupabaseStorageClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

@Configuration
public class StorageConfig {

    @Bean
    RestClient.Builder storageRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "app.storage.provider", havingValue = "r2", matchIfMissing = false)
    public FileStorageService r2StorageService(StorageProperties storageProperties, RestClient.Builder restClientBuilder) {
        return new CloudflareR2StorageClient(storageProperties);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "app.storage.provider", havingValue = "supabase", matchIfMissing = true)
    public FileStorageService supabaseStorageService(StorageProperties storageProperties, RestClient.Builder restClientBuilder) {
        return new SupabaseStorageClient(storageProperties, restClientBuilder);
    }
}
