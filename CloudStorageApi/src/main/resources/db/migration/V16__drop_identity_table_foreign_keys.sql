DROP PROCEDURE IF EXISTS cloud_drop_foreign_key_if_exists;

DELIMITER //

CREATE PROCEDURE cloud_drop_foreign_key_if_exists(
    IN p_table_name VARCHAR(64),
    IN p_constraint_name VARCHAR(64)
)
BEGIN
    DECLARE constraint_count INT DEFAULT 0;

    SELECT COUNT(*)
      INTO constraint_count
      FROM information_schema.table_constraints
     WHERE table_schema = DATABASE()
       AND table_name = p_table_name
       AND constraint_name = p_constraint_name
       AND constraint_type = 'FOREIGN KEY';

    IF constraint_count > 0 THEN
        SET @cloud_ddl = CONCAT(
            'ALTER TABLE `',
            p_table_name,
            '` DROP FOREIGN KEY `',
            p_constraint_name,
            '`'
        );
        PREPARE cloud_statement FROM @cloud_ddl;
        EXECUTE cloud_statement;
        DEALLOCATE PREPARE cloud_statement;
    END IF;
END//

DELIMITER ;

CALL cloud_drop_foreign_key_if_exists('storage_node', 'fk_storage_node_owner');
CALL cloud_drop_foreign_key_if_exists('storage_node', 'fk_storage_node_deleted_by');
CALL cloud_drop_foreign_key_if_exists('multipart_upload_session', 'fk_multipart_upload_session_owner');
CALL cloud_drop_foreign_key_if_exists('share_link', 'fk_share_link_owner');
CALL cloud_drop_foreign_key_if_exists('app_package_release', 'fk_app_package_release_uploaded_by');
CALL cloud_drop_foreign_key_if_exists('cloud_user_profile', 'fk_cloud_user_profile_identity_user');

DROP PROCEDURE IF EXISTS cloud_drop_foreign_key_if_exists;
