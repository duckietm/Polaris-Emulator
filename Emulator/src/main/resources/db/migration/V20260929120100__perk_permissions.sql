-- The client perks that were always on get a permission key each, so a hotel can switch one off
-- per rank (set the rank_<id> column to 0). Every rank gets 1, so nothing changes until then.
-- A rank without the key keeps the perk on. Idempotent.

INSERT INTO `permission_definitions` (`permission_key`, `max_value`, `comment`) VALUES
    ('acc_perk_vote_in_competitions', 1, 'Client perk VOTE_IN_COMPETITIONS.'),
    ('acc_perk_call_on_helpers', 1, 'Client perk CALL_ON_HELPERS.'),
    ('acc_perk_citizen', 1, 'Client perk CITIZEN.'),
    ('acc_perk_builder_at_work', 1, 'Client perk BUILDER_AT_WORK.'),
    ('acc_perk_navigator_phase_two', 1, 'Client perk NAVIGATOR_PHASE_TWO_2014 (the new navigator).'),
    ('acc_perk_mouse_zoom', 1, 'Client perk MOUSE_ZOOM.'),
    ('acc_perk_navigator_thumbnail_camera', 1, 'Client perk NAVIGATOR_ROOM_THUMBNAIL_CAMERA.'),
    ('acc_perk_habbo_club_offer_beta', 1, 'Client perk HABBO_CLUB_OFFER_BETA.')
ON DUPLICATE KEY UPDATE `comment` = VALUES(`comment`);

SET @grant_sql = NULL;
SELECT GROUP_CONCAT(CONCAT('`', c.`column_name`, '` = 1') ORDER BY r.`id` SEPARATOR ', ')
  INTO @grant_sql
FROM information_schema.columns c
JOIN `permission_ranks` r ON c.`column_name` = CONCAT('rank_', r.`id`)
WHERE c.`table_schema` = DATABASE()
  AND c.`table_name` = 'permission_definitions';
SET @grant_sql = IF(@grant_sql IS NULL,
    'SELECT 1',
    CONCAT('UPDATE `permission_definitions` SET ', @grant_sql, ' WHERE `permission_key` IN (''acc_perk_vote_in_competitions'', ''acc_perk_call_on_helpers'', ''acc_perk_citizen'', ''acc_perk_builder_at_work'', ''acc_perk_navigator_phase_two'', ''acc_perk_mouse_zoom'', ''acc_perk_navigator_thumbnail_camera'', ''acc_perk_habbo_club_offer_beta'')'));
PREPARE grant_stmt FROM @grant_sql;
EXECUTE grant_stmt;
DEALLOCATE PREPARE grant_stmt;
