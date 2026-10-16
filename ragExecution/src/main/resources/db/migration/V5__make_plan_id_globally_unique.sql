ALTER TABLE rag_execution
    ADD CONSTRAINT uk_rag_execution_plan_id
        UNIQUE (plan_id);
