package com.alicia.cloudstorage.identity.mail;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AliciaMailProperties.class)
public class EmailSenderConfiguration {

    @Bean
    public EmailSender emailSender(AliciaMailProperties properties) {
        if (!properties.isEnabled()) {
            return new DisabledEmailSender();
        }

        return new SmtpEmailSender(properties);
    }
}
