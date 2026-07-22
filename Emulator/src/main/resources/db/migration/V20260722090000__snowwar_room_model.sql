-- SnowWar arena stored in the database, like the original Habbo public rooms.
--
-- * room_models row 'snowstorm_arena_1': the arena heightmap lives in the heightmap
--   column; the layout (obstacles, snowball machines, spawn clusters) lives
--   in the public_items column, one entry per line:
--       <classname> <x> <y> <rotation> [walkableHeight collisionHeight]
--       snowball_machine <x> <y>
--       spawn <x> <y> <width> <height>
--   SnowWarMapsManager prefers this row over the bundled files and the
--   :snowwarsave command rewrites the furniture lines from the editor room.
-- * acc_snowwar_edit (rank 7 only): shows the in-game Edit Room button and
--   allows opening the arena editor room.
-- * cmd_snowwar_save (rank 7 only): :snowwarsave publishes the editor
--   room's furniture into public_items.

INSERT IGNORE INTO `room_models` (`name`, `door_x`, `door_y`, `door_dir`, `heightmap`, `public_items`, `club_only`) VALUES
	('snowstorm_arena_1', 0, 0, 2, '00000000000000000000000000000000000000000000000000\r\nxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx\r\nxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx\r\nxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx\r\nxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx\r\nxxxxxxxxxxxxxx000000000000000xxxxxxxxxxxxxxxxxxxxx\r\nxxxxxxxxxxxxx00000000000000000xxxxxxxxxxxxxxxxxxxx\r\nxxxxxxxxxxxx0000000000000000000xxxxxxxxxxxxxxxxxxx\r\nxxxxxxxxxxx000000000000000000000xxxxxxxxxxxxxxxxxx\r\nxxxxxxxxxx00000000000000000000000xxxxxxxxxxxxxxxxx\r\nxxxxxxxxx0000000000000000000000000xxxxxxxxxxxxxxxx\r\nxxxxxxxx000000000000000000000000000xxxxxxxxxxxxxxx\r\nxxxxxxx00000000000000000000000000000xxxxxxxxxxxxxx\r\nxxxxxx0000000000000000000000000000000xxxxxxxxxxxxx\r\nxxxxx000000000000000000000000000000000xxxxxxxxxxxx\r\nxxxxx0000000000000000000000000000000000xxxxxxxxxxx\r\nxxxxx00000000000000000000000000000000000xxxxxxxxxx\r\nxxxxx000000000000000000000000000000000000xxxxxxxxx\r\nxxxxx0000000000000000000000000000000000000xxxxxxxx\r\nxxxxx00000000000000000000000000000000000000xxxxxxx\r\nxxxxx000000000000000000000000000000000000000xxxxxx\r\nxxxxx0000000000000000000000000000000000000000xxxxx\r\n0xxxx00000000000000000000000000000000000000000xxxx\r\nxxxxx00000000000000000000000000000000000000000xxxx\r\nxxxxx00000000000000000000000000000000000000000xxxx\r\nxxxxx000000000000000000000000000000000000000000xxx\r\nxxxxx000000000000000000000000000000000000000000xxx\r\nxxxxx000000000000000000000000000000000000000000xxx\r\nxxxxxx00000000000000000000000000000000000000000xxx\r\nxxxxxxx0000000000000000000000000000000000000000xxx\r\nxxxxxxxx0000000000000000000000000000000000000xxxxx\r\nxxxxxxxxx00000000000000000000000000000000000xxxxxx\r\nxxxxxxxxxx000000000000000000000000000000000xxxxxxx\r\nxxxxxxxxxxx00000000000000000000000000000000xxxx0xx\r\nxxxxxxxxxxxx0000000000000000000000000000000xxxxxxx\r\nxxxxxxxxxxxxx00000000000000000000000000000xxxxxxxx\r\nxxxxxxxxxxxxxx0000000000000000000000000000xxxxxxxx\r\nxxxxxxxxxxxxxxx00000000000000000000000000xxxxxxxxx\r\nxxxxxxxxxxxxxxxx0000000000000000000000000xxxxxxxxx\r\nxxxxxxxxxxxxxxxxx00000000000000000000000xxxxxxxxxx\r\nxxxxxxxxxxxxxxxxxx0000000000000000000000xxxxxxxxxx\r\nxxxxxxxxxxxxxxxxxxx00000000000000000000xxxxxxxxxxx\r\nxxxxxxxxxxxxxxxxxxxxxxx000000000000000xxxxxxxxxxxx\r\nxxxxxxxxxxxxxxxxxxxxxxxx0000000000000xxxxxxxxxxxxx\r\nxxxxxxxxxxxxxxxxxxxxxxxxx00000000000xxxxxxxxxxxxxx\r\nxxxxxxxxxxxxxxxxxxxxxxxxxx0000000xxxxxxxxxxxxxxxxx\r\nxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx\r\nxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx\r\nxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx\r\nxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx', 'snowball_machine 8 22\r\nsnowball_machine 40 27\r\nspawn 25 8 3 1\r\nspawn 10 25 3 1\r\nspawn 40 25 3 1\r\nspawn 30 42 3 1\r\nsw_tree1 14 10 0\r\nsw_tree2 33 12 0\r\nsw_tree1 10 28 0\r\nsw_tree2 40 33 0\r\nblock_basic 20 15 0\r\nblock_basic 30 15 2\r\nblock_ice 15 22 0\r\nblock_ice 36 25 4\r\nobst_snowman 25 12 0\r\nobst_snowman 25 38 0\r\nblock_basic 18 33 6\r\nblock_ice 32 35 0', '0');

INSERT INTO `permission_definitions` (`permission_key`, `max_value`, `comment`, `rank_1`, `rank_2`, `rank_3`, `rank_4`, `rank_5`, `rank_6`, `rank_7`) VALUES
	('acc_snowwar_edit', 1, 'Shows the SnowWar Edit Room button and allows opening the arena editor room.', 0, 0, 0, 0, 0, 0, 1),
	('cmd_snowwar_save', 1, 'Allows :snowwarsave to publish the editor room furniture into room_models.public_items.', 0, 0, 0, 0, 0, 0, 1)
ON DUPLICATE KEY UPDATE `comment` = VALUES(`comment`);

INSERT INTO `emulator_texts` (`key`, `value`) VALUES
	('commands.keys.cmd_snowwar_save', 'snowwarsave'),
	('commands.description.cmd_snowwar_save', ':snowwarsave - publish the SnowWar editor room layout'),
	('commands.success.cmd_snowwar_save', 'SnowWar arena saved (%count% items, %ads% ad images). The next game uses the new layout.'),
	('commands.error.cmd_snowwar_save.wrong_room', 'This room does not use the SnowWar arena model.'),
	('commands.error.cmd_snowwar_save.no_model', 'The SnowWar room model row is missing in room_models.')
ON DUPLICATE KEY UPDATE `value` = `value`;
