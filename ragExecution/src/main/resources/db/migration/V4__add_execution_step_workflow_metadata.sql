ALTER TABLE rag_execution_step
    ADD COLUMN step_key VARCHAR(64) NULL;

ALTER TABLE rag_execution_step
    ADD COLUMN depends_on_keys VARCHAR(650) NOT NULL DEFAULT '';

ALTER TABLE rag_execution_step
    ADD COLUMN output_key VARCHAR(64) NULL;

ALTER TABLE rag_execution_step
    ADD COLUMN required_client_fields VARCHAR(255) NOT NULL DEFAULT '';

UPDATE rag_execution_step
SET step_key = CONCAT('step_', step_index)
WHERE step_key IS NULL;

ALTER TABLE rag_execution_step
    MODIFY COLUMN step_key VARCHAR(64) NOT NULL;

ALTER TABLE rag_execution_step
    ADD CONSTRAINT uk_rag_execution_step_key UNIQUE (execution_id, step_key);
