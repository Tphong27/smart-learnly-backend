package com.smartlearnly.backend.videoai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Compatibility mapper used by existing services that still consume
 * Jackson 2 types while Spring Boot's HTTP layer uses Jackson 3.
 */
@Configuration(proxyBeanMethods = false)
public class VideoAiJacksonConfig {

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    ObjectMapper jackson2ObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
