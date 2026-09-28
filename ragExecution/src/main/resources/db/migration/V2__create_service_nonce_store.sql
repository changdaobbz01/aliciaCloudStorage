CREATE TABLE rag_execution_service_nonce (
    id VARCHAR(128) PRIMARY KEY,
    caller_service VARCHAR(32) NOT NULL,
    nonce VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_rag_execution_service_nonce UNIQUE (caller_service, nonce)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_rag_execution_service_nonce_expiry
    ON rag_execution_service_nonce (expires_at);
