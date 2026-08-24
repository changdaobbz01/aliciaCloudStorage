DROP PROCEDURE IF EXISTS cloud_drop_column_if_exists;

DELIMITER //

CREATE PROCEDURE cloud_drop_column_if_exists(
    IN p_table_name VARCHAR(64),
    IN p_column_name VARCHAR(64)
)
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = p_table_name
          AND column_name = p_column_name
    ) THEN
        SET @cloud_ddl = CONCAT('ALTER TABLE `', p_table_name, '` DROP COLUMN `', p_column_name, '`');
        PREPARE cloud_statement FROM @cloud_ddl;
        EXECUTE cloud_statement;
        DEALLOCATE PREPARE cloud_statement;
    END IF;
END//

DELIMITER ;

CALL cloud_drop_column_if_exists('sys_user', 'storage_quota_bytes');
CALL cloud_drop_column_if_exists('sys_user', 'home_background_url');

DROP PROCEDURE IF EXISTS cloud_drop_column_if_exists;
