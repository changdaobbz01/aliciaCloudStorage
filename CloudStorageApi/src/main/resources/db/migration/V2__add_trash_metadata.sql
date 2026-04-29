ALTER TABLE storage_node
    ADD COLUMN original_parent_id BIGINT NULL AFTER parent_id,
    ADD COLUMN deleted_at DATETIME NULL AFTER is_deleted,
    ADD COLUMN deleted_by BIGINT NULL AFTER deleted_at;

ALTER TABLE storage_node
    ADD CONSTRAINT fk_storage_node_original_parent
        FOREIGN KEY (original_parent_id) REFERENCES storage_node(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_storage_node_deleted_by
        FOREIGN KEY (deleted_by) REFERENCES sys_user(id);

CREATE INDEX idx_storage_node_owner_deleted_at
    ON storage_node (owner_id, is_deleted, deleted_at);

UPDATE storage_node
SET deleted_at = updated_at
WHERE is_deleted = 1
  AND deleted_at IS NULL;

UPDATE storage_node
SET original_parent_id = parent_id
WHERE is_deleted = 1
  AND original_parent_id IS NULL;
