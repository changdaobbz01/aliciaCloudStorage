package com.alicia.cloudstorage.ragexecution.port;

import java.time.Instant;

public interface ServiceNonceStore {

    void consume(String callerService, String nonce, Instant expiresAt, Instant now);
}
