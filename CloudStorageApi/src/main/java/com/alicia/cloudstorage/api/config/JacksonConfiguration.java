package com.alicia.cloudstorage.api.config;

import com.alicia.cloudstorage.api.serialization.LocalDateTimeWithSystemOffsetSerializer;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.module.SimpleModule;

import java.time.LocalDateTime;

@Configuration
public class JacksonConfiguration {

    @Bean
    public JsonMapperBuilderCustomizer localDateTimeWithSystemOffsetCustomizer() {
        return builder -> {
            SimpleModule localDateTimeModule = new SimpleModule("local-date-time-with-system-offset");
            localDateTimeModule.addSerializer(
                    LocalDateTime.class,
                    new LocalDateTimeWithSystemOffsetSerializer()
            );
            builder.addModule(localDateTimeModule)
                    .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS);
        };
    }
}
