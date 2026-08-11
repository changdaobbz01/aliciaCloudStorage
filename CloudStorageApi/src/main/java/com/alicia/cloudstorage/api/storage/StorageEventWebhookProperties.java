package com.alicia.cloudstorage.api.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "alicia.storage-events.webhook")
public class StorageEventWebhookProperties {

    private String url = "";
    private String secret = "";
    private long connectTimeoutMs = 2_000L;
    private long requestTimeoutMs = 5_000L;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url == null ? "" : url.trim();
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret == null ? "" : secret.trim();
    }

    public long getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(long connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public long getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    public void setRequestTimeoutMs(long requestTimeoutMs) {
        this.requestTimeoutMs = requestTimeoutMs;
    }

    public boolean isEnabled() {
        return !url.isBlank();
    }

    public void validateEnabledConfiguration() {
        if (isEnabled() && secret.isBlank()) {
            throw new IllegalStateException("Storage event webhook secret must be configured when webhook url is set.");
        }
    }
}
