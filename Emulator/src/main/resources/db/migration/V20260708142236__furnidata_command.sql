-- :furnidata command
-- Toggles furni inspection mode: while enabled, clicking (using) a floor or
-- wall item shows its items_base and room item data instead of toggling it.
-- Replaces the old FurniData plugin; the command is now native emulator code.

-- Command texts
INSERT INTO `emulator_texts` (`key`, `value`) VALUES
	('commands.keys.cmd_furnidata', 'furnidata'),
	('commands.description.cmd_furnidata', ':furnidata'),
	('furnidata.cmd_furnidata.on', 'Furnidata inspection enabled. Click a furni to see its data, use :furnidata again to turn it off.'),
	('furnidata.cmd_furnidata.off', 'Furnidata inspection disabled.')
ON DUPLICATE KEY UPDATE `value` = VALUES(`value`);

-- Permission (normalized schema) - staff only by default (rank 7)
INSERT INTO `permission_definitions` (`permission_key`, `max_value`, `comment`, `rank_1`, `rank_2`, `rank_3`, `rank_4`, `rank_5`, `rank_6`, `rank_7`) VALUES
	('cmd_furnidata', 1, 'Allows using :furnidata to inspect items_base and room item data by clicking furniture.', 0, 0, 0, 0, 0, 0, 1)
ON DUPLICATE KEY UPDATE `comment` = VALUES(`comment`);

-- If the old FurniData plugin ever ran on this database, it added a
-- cmd_furnidata column to the legacy permissions table. That column is no
-- longer used and can be removed manually:
--   ALTER TABLE `permissions` DROP COLUMN `cmd_furnidata`;


INSERT INTO `emulator_texts` (`key`, `value`)
VALUES ('stickypole.click.info', 'Place a sticky note from your inventory on a wall to leave a message!')
ON DUPLICATE KEY UPDATE `value` = VALUES(`value`);
