-- /api/emustats (the live stats page) gets its own permission. Idempotent.
--   acc_emustats   read /api/emustats: online users, active rooms, wired load, pool and scheduler metrics
-- Until now the page asked for acc_modtool_room_info, so every moderator could see it. The new key starts with exactly
-- the values of acc_modtool_room_info, so nobody loses or gains access; change the rank_* columns to narrow it.
-- The values are copied only when the key is created, never over a hotel's own choice on a rerun.

SET @emustats_existed = (SELECT COUNT(*) FROM `permission_definitions` WHERE `permission_key` = 'acc_emustats');

INSERT INTO `permission_definitions` (`permission_key`, `max_value`, `comment`) VALUES
    ('acc_emustats', 1, 'Allows reading /api/emustats, the live server statistics page.')
ON DUPLICATE KEY UPDATE `comment` = VALUES(`comment`);

SET @copy_sql = NULL;
SELECT GROUP_CONCAT(CONCAT('target.`', c.`column_name`, '` = source.`', c.`column_name`, '`') ORDER BY r.`id` SEPARATOR ', ')
  INTO @copy_sql
FROM information_schema.columns c
JOIN `permission_ranks` r ON c.`column_name` = CONCAT('rank_', r.`id`)
WHERE c.`table_schema` = DATABASE()
  AND c.`table_name` = 'permission_definitions';
SET @copy_sql = IF(@copy_sql IS NULL OR @emustats_existed > 0,
    'SELECT 1',
    CONCAT('UPDATE `permission_definitions` target',
           ' JOIN `permission_definitions` source ON source.`permission_key` = ''acc_modtool_room_info''',
           ' SET ', @copy_sql,
           ' WHERE target.`permission_key` = ''acc_emustats'''));
PREPARE copy_stmt FROM @copy_sql;
EXECUTE copy_stmt;
DEALLOCATE PREPARE copy_stmt;
