DROP PROCEDURE IF EXISTS identity_rename_table_if_needed;

DELIMITER //

CREATE PROCEDURE identity_rename_table_if_needed(
    IN p_old_table_name VARCHAR(64),
    IN p_new_table_name VARCHAR(64)
)
BEGIN
    DECLARE old_table_count INT DEFAULT 0;
    DECLARE new_table_count INT DEFAULT 0;

    SELECT COUNT(*)
      INTO old_table_count
      FROM information_schema.tables
     WHERE table_schema = DATABASE()
       AND table_name = p_old_table_name;

    SELECT COUNT(*)
      INTO new_table_count
      FROM information_schema.tables
     WHERE table_schema = DATABASE()
       AND table_name = p_new_table_name;

    IF old_table_count > 0 AND new_table_count = 0 THEN
        SET @identity_ddl = CONCAT('RENAME TABLE `', p_old_table_name, '` TO `', p_new_table_name, '`');
        PREPARE identity_statement FROM @identity_ddl;
        EXECUTE identity_statement;
        DEALLOCATE PREPARE identity_statement;
    ELSEIF old_table_count > 0 AND new_table_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Both sys_user and identity_user exist; refusing ambiguous identity table rename.';
    ELSEIF old_table_count = 0 AND new_table_count = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Neither sys_user nor identity_user exists; cannot finalize identity table name.';
    END IF;
END//

DELIMITER ;

CALL identity_rename_table_if_needed('sys_user', 'identity_user');

DROP PROCEDURE IF EXISTS identity_rename_table_if_needed;

DROP PROCEDURE IF EXISTS identity_rename_index_if_needed;

DELIMITER //

CREATE PROCEDURE identity_rename_index_if_needed(
    IN p_table_name VARCHAR(64),
    IN p_old_index_name VARCHAR(64),
    IN p_new_index_name VARCHAR(64)
)
BEGIN
    DECLARE old_index_count INT DEFAULT 0;
    DECLARE new_index_count INT DEFAULT 0;

    SELECT COUNT(*)
      INTO old_index_count
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = p_table_name
       AND index_name = p_old_index_name;

    SELECT COUNT(*)
      INTO new_index_count
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = p_table_name
       AND index_name = p_new_index_name;

    IF old_index_count > 0 AND new_index_count = 0 THEN
        SET @identity_ddl = CONCAT(
            'ALTER TABLE `',
            p_table_name,
            '` RENAME INDEX `',
            p_old_index_name,
            '` TO `',
            p_new_index_name,
            '`'
        );
        PREPARE identity_statement FROM @identity_ddl;
        EXECUTE identity_statement;
        DEALLOCATE PREPARE identity_statement;
    END IF;
END//

DELIMITER ;

CALL identity_rename_index_if_needed('identity_user', 'uk_sys_user_email', 'uk_identity_user_email');

DROP PROCEDURE IF EXISTS identity_rename_index_if_needed;
