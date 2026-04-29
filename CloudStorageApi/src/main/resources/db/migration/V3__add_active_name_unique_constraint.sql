ALTER TABLE storage_node
    ADD COLUMN parent_scope_id BIGINT NOT NULL DEFAULT 0 AFTER original_parent_id,
    ADD COLUMN active_node_name VARCHAR(255) NULL AFTER is_deleted;

UPDATE storage_node
SET parent_scope_id = COALESCE(parent_id, 0),
    active_node_name = CASE WHEN is_deleted = 0 THEN node_name ELSE NULL END;

CREATE UNIQUE INDEX uk_storage_node_owner_parent_active_name
    ON storage_node (owner_id, parent_scope_id, active_node_name);
