-- Permission keys the code needs but no rank could hold. Idempotent.
--   cmd_roomfx           :roomfx (shake, zoom, rotate or disco the room); nobody could use it
--   cmd_calendar_staff   :calendar <campaign> opens another campaign than the default
--   acc_unload_any_room  :unload in a room you do not own (was rank id 5 and up in code)
--   acc_hotelview_edit   edit the hotel view landing page (was rank id 7 and up in code)
-- Each is granted to the ranks that had it before by rank number, now by rank level.

INSERT INTO `permission_definitions` (`permission_key`, `max_value`, `comment`) VALUES
    ('cmd_roomfx', 1, 'Allows :roomfx (rotate, shake, zoom or disco the room).'),
    ('cmd_calendar_staff', 1, 'Allows :calendar <campaign> to open a campaign other than the default.'),
    ('acc_unload_any_room', 1, 'Allows :unload in rooms the user does not own.'),
    ('acc_hotelview_edit', 1, 'Allows editing the hotel view landing page (scenes, widgets, votes).')
ON DUPLICATE KEY UPDATE `comment` = VALUES(`comment`);

-- rank_<id> columns exist per hotel, so only the ones present are touched.
SET @grant_sql = NULL;
SELECT GROUP_CONCAT(CONCAT('`', c.`column_name`, '` = 1') ORDER BY r.`id` SEPARATOR ', ')
  INTO @grant_sql
FROM information_schema.columns c
JOIN `permission_ranks` r ON c.`column_name` = CONCAT('rank_', r.`id`)
WHERE c.`table_schema` = DATABASE()
  AND c.`table_name` = 'permission_definitions'
  AND r.`level` >= 5;
SET @grant_sql = IF(@grant_sql IS NULL,
    'SELECT 1',
    CONCAT('UPDATE `permission_definitions` SET ', @grant_sql,
           ' WHERE `permission_key` IN (''cmd_roomfx'', ''cmd_calendar_staff'', ''acc_unload_any_room'')'));
PREPARE grant_stmt FROM @grant_sql;
EXECUTE grant_stmt;
DEALLOCATE PREPARE grant_stmt;

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
           ' WHERE `permission_key` = ''acc_hotelview_edit'''));
PREPARE grant_stmt FROM @grant_sql;
EXECUTE grant_stmt;
DEALLOCATE PREPARE grant_stmt;
