ALTER TABLE rag_execution
    ADD CONSTRAINT uk_rag_execution_owner_plan_id
        UNIQUE (owner_user_id, plan_id);
