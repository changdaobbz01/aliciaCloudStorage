package com.alicia.cloudstorage.ragexecution.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ExecutionInfrastructureConfig {

    @Bean
    public Clock executionClockSource() {
        return Clock.systemUTC();
    }

    @Bean
    public ObjectMapper executionObjectMapper() {
        return new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Bean(destroyMethod = "close")
    @Qualifier("executionStreamExecutor")
    public ExecutorService executionStreamExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
