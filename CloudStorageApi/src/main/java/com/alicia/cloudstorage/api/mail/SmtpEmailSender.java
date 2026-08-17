package com.alicia.cloudstorage.api.mail;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

class SmtpEmailSender implements EmailSender {

    private final JavaMailSenderImpl mailSender;
    private final String fromAddress;
    private final String fromName;

    SmtpEmailSender(AliciaMailProperties properties) {
        validate(properties);

        this.fromAddress = properties.getFromAddress().trim();
        this.fromName = properties.getFromName().trim().isEmpty()
                ? this.fromAddress
                : properties.getFromName().trim();
        this.mailSender = new JavaMailSenderImpl();
        this.mailSender.setHost(properties.getSmtpHost().trim());
        this.mailSender.setPort(properties.getSmtpPort());
        this.mailSender.setUsername(properties.getSmtpUsername().trim());
        this.mailSender.setPassword(properties.getSmtpPassword());
        this.mailSender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        this.mailSender.setJavaMailProperties(javaMailProperties(properties));
    }

    @Override
    public void sendText(String to, String subject, String body) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(fromAddress, fromName, StandardCharsets.UTF_8.name()));
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);
            mailSender.send(message);
        } catch (Exception ex) {
            throw new EmailDeliveryException("验证码邮件发送失败，请稍后再试。", ex);
        }
    }

    private Properties javaMailProperties(AliciaMailProperties properties) {
        Properties javaMailProperties = new Properties();
        javaMailProperties.put("mail.smtp.auth", "true");
        javaMailProperties.put("mail.smtp.connectiontimeout", String.valueOf(properties.getConnectionTimeoutMs()));
        javaMailProperties.put("mail.smtp.timeout", String.valueOf(properties.getReadTimeoutMs()));
        javaMailProperties.put("mail.smtp.writetimeout", String.valueOf(properties.getWriteTimeoutMs()));

        if (properties.isSslEnabled()) {
            javaMailProperties.put("mail.smtp.ssl.enable", "true");
        } else {
            javaMailProperties.put("mail.smtp.starttls.enable", "true");
        }

        return javaMailProperties;
    }

    private void validate(AliciaMailProperties properties) {
        if (isBlank(properties.getSmtpHost())
                || isBlank(properties.getSmtpUsername())
                || isBlank(properties.getSmtpPassword())
                || isBlank(properties.getFromAddress())) {
            throw new EmailDeliveryException("邮箱服务配置不完整，请检查 SMTP 环境变量。");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
