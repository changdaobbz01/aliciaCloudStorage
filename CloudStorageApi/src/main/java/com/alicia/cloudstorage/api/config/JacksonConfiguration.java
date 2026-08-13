package com.alicia.cloudstorage.api.config;

import com.alicia.cloudstorage.api.serialization.LocalDateTimeWithSystemOffsetSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

@Configuration
public class JacksonConfiguration {

    @Bean
    public ObjectMapper objectMapper() {
        SimpleModule localDateTimeModule = new SimpleModule("local-date-time-with-system-offset");
        localDateTimeModule.addSerializer(
                LocalDateTime.class,
                new LocalDateTimeWithSystemOffsetSerializer()
        );

        return new ObjectMapper()
                .findAndRegisterModules()
                .registerModule(localDateTimeModule)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
