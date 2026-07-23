-- WordGuesser plugin (Plugins/Wordguesser)
-- All database setup for the plugin lives in this migration; the plugin no
-- longer executes DDL, registers texts/settings, or touches permission
-- tables at runtime. Running the plugin on a database without this
-- migration falls back to built-in English defaults for texts/settings,
-- but the word list and command permissions require these rows.

-- Word list
CREATE TABLE IF NOT EXISTS `random_words` (
	`id` int(11) NOT NULL AUTO_INCREMENT,
	`word` varchar(64) NOT NULL,
	PRIMARY KEY (`id`),
	UNIQUE KEY `word` (`word`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO `random_words` (`word`) VALUES
	('pixel'), ('trophy'), ('throne'), ('dragon'), ('jukebox'),
	('teleport'), ('wardrobe'), ('moodlight'), ('marketplace'), ('penguin'),
	('campfire'), ('iceberg'), ('lagoon'), ('monster'), ('pumpkin'),
	('snowflake'), ('treasure'), ('victory'), ('wizard'), ('rainbow'),
	('rooftop'), ('skyline'), ('sunshine'), ('holiday'), ('mystery'),
	('fortune'), ('diamond'), ('emerald'), ('sapphire'), ('crystal'),
	('castle'), ('garden'), ('arcade'), ('cinema'), ('library'),
	('harbor'), ('football'), ('freeze'), ('banzai'), ('duckling');

-- Command texts. Existing values are kept (hotels may have customized them
-- when the old plugin registered these keys at runtime).
INSERT INTO `emulator_texts` (`key`, `value`) VALUES
	('commands.keys.cmd_randomword', 'rw'),
	('commands.description.cmd_randomword', ':rw <word>'),
	('commands.keys.cmd_update_words', 'update_words'),
	('commands.description.cmd_update_words', ':update_words'),
	('randomword.cmd_randomword.success', 'You got the word right! Congrats.'),
	('randomword.cmd_randomword.error', 'Please fill in a word'),
	('randomword.cmd_randomword.wrong', 'You guessed the wrong word'),
	('randomword.cmd_randomword.no_word', 'There is no word to guess right now'),
	('randomword.new_word', 'The word is scrambled. Beat it! "%word%" use :rw (word)'),
	('randomword.nobody', 'Nobody got the right word, the word was %word%'),
	('randomword.winner', 'User %username% got the right word "%word%" and won the prize.'),
	('randomword.cmd_update_words.success', 'Successfully updated random words')
ON DUPLICATE KEY UPDATE `value` = `value`;

-- Settings. Existing values are kept.
-- currency_type: -1 = credits, otherwise a points type for givePoints().
INSERT INTO `emulator_settings` (`key`, `value`) VALUES
	('randomword.prize.currency_type', '5'),
	('randomword.prize.currency_amount', '1'),
	('randomword.prize.badge', ''),
	('randomword.interval_seconds', '1800'),
	('randomword.guess_window_seconds', '300')
ON DUPLICATE KEY UPDATE `value` = `value`;

-- Permissions (normalized schema): guessing is open to every rank,
-- reloading the word list is staff only (rank 7).
INSERT INTO `permission_definitions` (`permission_key`, `max_value`, `comment`, `rank_1`, `rank_2`, `rank_3`, `rank_4`, `rank_5`, `rank_6`, `rank_7`) VALUES
	('cmd_randomword', 1, 'Allows using :rw to guess the scrambled Word Guesser word.', 1, 1, 1, 1, 1, 1, 1),
	('cmd_update_words', 1, 'Allows using :update_words to reload the Word Guesser word list.', 0, 0, 0, 0, 0, 0, 1)
ON DUPLICATE KEY UPDATE `comment` = VALUES(`comment`);

-- If the old plugin ever ran on this database it added ENUM columns to the
-- legacy `permissions` table. Those are no longer read and can be removed
-- manually:
--   ALTER TABLE `permissions` DROP COLUMN `cmd_randomword`;
--   ALTER TABLE `permissions` DROP COLUMN `cmd_update_words`;
