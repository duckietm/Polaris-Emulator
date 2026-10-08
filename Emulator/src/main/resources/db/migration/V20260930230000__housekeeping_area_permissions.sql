-- Housekeeping areas: each part of the panel behind its own key on top of acc_housekeeping.
--   acc_hk_users        users: lookup, kick, disconnect, mute, trade lock, password, notes
--   acc_hk_bans         bans: ban, unban, revoke one ban, the bans list
--   acc_hk_economy      economy: credits, currencies, items, HC
--   acc_hk_rooms        rooms: lookup, state, mute, kick all, transfer, delete, settings
--   acc_hk_permissions  the permission matrix and rank changes
--   acc_hk_hotel        hotel tools: alerts, reloads, maintenance, word filter, lockdown
-- Every rank gets each key with the value it has for acc_housekeeping, so nobody loses what
-- they could do; hotels narrow them from the permission matrix. Idempotent.

INSERT INTO `permission_definitions` (`permission_key`, `max_value`, `comment`) VALUES
    ('acc_hk_users', 1, 'Housekeeping: the users area (lookup, kick, mute, trade lock, password, notes).'),
    ('acc_hk_bans', 1, 'Housekeeping: the bans area (ban, unban, revoke, bans list).'),
    ('acc_hk_economy', 1, 'Housekeeping: the economy area (credits, currencies, items, HC).'),
    ('acc_hk_rooms', 1, 'Housekeeping: the rooms area (state, mute, kick all, transfer, delete, settings).'),
    ('acc_hk_permissions', 1, 'Housekeeping: the permission matrix and rank changes.'),
    ('acc_hk_hotel', 1, 'Housekeeping: the hotel tools (alerts, reloads, maintenance, word filter, lockdown).')
ON DUPLICATE KEY UPDATE `comment` = VALUES(`comment`);

-- rank_<id> columns exist per hotel: copy each present one from the acc_housekeeping row.
SET @copy_sql = NULL;
SELECT GROUP_CONCAT(CONCAT('area.`', c.`column_name`, '` = hk.`', c.`column_name`, '`') ORDER BY r.`id` SEPARATOR ', ')
  INTO @copy_sql
FROM information_schema.columns c
JOIN `permission_ranks` r ON c.`column_name` = CONCAT('rank_', r.`id`)
WHERE c.`table_schema` = DATABASE()
  AND c.`table_name` = 'permission_definitions';
SET @copy_sql = IF(@copy_sql IS NULL,
    'SELECT 1',
    CONCAT('UPDATE `permission_definitions` area JOIN `permission_definitions` hk',
           ' ON hk.`permission_key` = ''acc_housekeeping''',
           ' SET ', @copy_sql,
           ' WHERE area.`permission_key` IN (''acc_hk_users'', ''acc_hk_bans'', ''acc_hk_economy'',',
           ' ''acc_hk_rooms'', ''acc_hk_permissions'', ''acc_hk_hotel'')'));
PREPARE copy_stmt FROM @copy_sql;
EXECUTE copy_stmt;
DEALLOCATE PREPARE copy_stmt;
