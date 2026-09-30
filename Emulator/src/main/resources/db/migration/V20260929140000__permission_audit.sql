-- Permission audit: every rank a user is given (:give_rank, RCON, housekeeping) and every rank or
-- key value :update_permissions finds changed in the tables, with who did it and how.
-- Rows are only added. Also adds :perm (check a user's key, read this audit), for the ranks
-- that may run :update_permissions. Idempotent.

CREATE TABLE IF NOT EXISTS `permission_audit` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `timestamp` int(11) NOT NULL,
  `actor_id` int(11) NOT NULL DEFAULT 0,
  `actor_name` varchar(64) NOT NULL DEFAULT '',
  `action` varchar(32) NOT NULL,
  `target_type` varchar(16) NOT NULL,
  `target_id` int(11) NOT NULL,
  `subject` varchar(64) NOT NULL DEFAULT '',
  `old_value` varchar(64) NOT NULL DEFAULT '',
  `new_value` varchar(64) NOT NULL DEFAULT '',
  `via` varchar(64) NOT NULL DEFAULT '',
  PRIMARY KEY (`id`),
  KEY `target` (`target_type`, `target_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `permission_definitions` (`permission_key`, `max_value`, `comment`) VALUES
    ('cmd_perm', 1, 'Allows :perm check <user> <key> and :perm log [user].')
ON DUPLICATE KEY UPDATE `comment` = VALUES(`comment`);

SET @copy_sql = NULL;
SELECT GROUP_CONCAT(CONCAT('d.`', c.`column_name`, '` = s.`', c.`column_name`, '`') ORDER BY r.`id` SEPARATOR ', ')
  INTO @copy_sql
FROM information_schema.columns c
JOIN `permission_ranks` r ON c.`column_name` = CONCAT('rank_', r.`id`)
WHERE c.`table_schema` = DATABASE()
  AND c.`table_name` = 'permission_definitions';
SET @copy_sql = IF(@copy_sql IS NULL,
    'SELECT 1',
    CONCAT('UPDATE `permission_definitions` d JOIN `permission_definitions` s ON s.`permission_key` = ''cmd_update_permissions''',
           ' SET ', @copy_sql, ' WHERE d.`permission_key` = ''cmd_perm'''));
PREPARE copy_stmt FROM @copy_sql;
EXECUTE copy_stmt;
DEALLOCATE PREPARE copy_stmt;

INSERT IGNORE INTO `emulator_texts` (`key`, `value`) VALUES
    ('commands.description.cmd_perm', ':perm check <user> <key> | :perm log [user]');
