-- Refresh the :snowwarsave success text so it reports how many room-ad
-- images were captured (helps confirm an ads_bg furni's URL was saved).
-- The original insert used ON DUPLICATE KEY UPDATE value=value, so this
-- forced update is needed on databases that already have the old text.
UPDATE `emulator_texts`
SET `value` = 'SnowWar arena saved (%count% items, %ads% ad images). The next game uses the new layout.'
WHERE `key` = 'commands.success.cmd_snowwar_save';
