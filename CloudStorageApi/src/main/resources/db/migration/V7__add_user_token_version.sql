ALTER TABLE sys_user
    ADD COLUMN token_version BIGINT NOT NULL DEFAULT 0 AFTER password_hash;
