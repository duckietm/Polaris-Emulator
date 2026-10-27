-- The helper talent track (AIR's "Helping others": levels 0-8) had no levels, so its window showed only the begin
-- pane. Each level follows the texts of talent.track.helper.level.<n>, built from the guide achievements the server
-- awards. A hotel that already has helper levels keeps its own.
--   0 The basics: registered.
--   1 No longer a rookie: learn the chat and room safety settings.
--   2 A helping hand: ask a guide for help and rate the guide.
--   3 With a little help from my friends: read about helpers and pass the safety quiz.
--   4 Frank's right hand: ten days in the hotel and five guide ratings; unlocks becoming a guide.
--   5 Habbo Helper: handle help requests as a guide.
--   6-7 Beginning Guardian, Guardian: handle more requests and earn recommendations (6 unlocks guardian).
--   8 To be released.
-- The perks only name what the level unlocks; who may use the guide tool still follows the permissions.

INSERT INTO `achievements_talents` (`type`, `level`, `achievement_ids`, `achievement_levels`, `reward_furni`, `reward_perks`, `reward_badges`)
SELECT rows_to_add.* FROM (
    SELECT 'helper' AS `type`, 0 AS `level`, '481' AS `achievement_ids`, '1' AS `achievement_levels`, '' AS `reward_furni`, '' AS `reward_perks`, '' AS `reward_badges`
    UNION ALL SELECT 'helper', 1, '2548,2549,2547,2369', '1,1,1,1', '', '', ''
    UNION ALL SELECT 'helper', 2, '1716,1653', '1,1', '', '', ''
    UNION ALL SELECT 'helper', 3, '1903,1705', '1,1', '', '', ''
    UNION ALL SELECT 'helper', 4, '481,1653', '3,2', '', 'USE_GUIDE_TOOL,GIVE_GUIDE_TOURS', ''
    UNION ALL SELECT 'helper', 5, '1706,1479', '1,1', '', '', ''
    UNION ALL SELECT 'helper', 6, '1706,1646', '3,1', '', 'JUDGE_CHAT_REVIEWS', ''
    UNION ALL SELECT 'helper', 7, '1706,1646', '5,3', '', '', ''
    UNION ALL SELECT 'helper', 8, '1706,1646', '7,5', '', '', ''
) AS rows_to_add
WHERE NOT EXISTS (SELECT 1 FROM `achievements_talents` WHERE `type` = 'helper');
