-- acc_hk_view_private: see IP addresses in clear in the housekeeping panel (masked otherwise; every
-- reveal is audited). Granted by rank level to the administrators (level 7 and up). Idempotent.

INSERT INTO `permission_definitions` (`permission_key`, `max_value`, `comment`) VALUES
    ('acc_hk_view_private', 1, 'Allows seeing IP addresses in clear in the housekeeping panel.')
ON DUPLICATE KEY UPDATE `comment` = VALUES(`comment`);

-- rank_<id> columns exist per hotel, so only the ones present are touched.
SET @grant_sql = NULL;
SELECT GROUP_CONCAT(CONCAT('`', c.`column_name`, '` = 1') ORDER BY r.`id` SEPARATOR ', ')
  INTO @grant_sql
FROM information_schema.columns c
JOIN `permission_ranks` r ON c.`column_name` = CONCAT('rank_', r.`id`)
WHERE c.`table_schema` = DATABASE()
  AND c.`table_name` = 'permission_definitions'
  AND r.`level` >= 7;
SET @grant_sql = IF(@grant_sql IS NULL,
    'SELECT 1',
    CONCAT('UPDATE `permission_definitions` SET ', @grant_sql,
           ' WHERE `permission_key` = ''acc_hk_view_private'''));
PREPARE grant_stmt FROM @grant_sql;
EXECUTE grant_stmt;
DEALLOCATE PREPARE grant_stmt;
