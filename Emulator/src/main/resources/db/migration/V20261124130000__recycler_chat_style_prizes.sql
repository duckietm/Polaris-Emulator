-- A Furni-Matic prize can be a chat style (the official client's prize type "chat_style").
-- recycler_prizes gets `chat_style_id`: 0 for a furni prize, as every existing row is; above 0 the row is a chat style
-- of that id at the row's rarity, and its item_id is not used.
--
-- Re-runnable: the column is only added when information_schema does not list it.
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'recycler_prizes' AND COLUMN_NAME = 'chat_style_id') = 0,
    'ALTER TABLE `recycler_prizes` ADD COLUMN `chat_style_id` INT NOT NULL DEFAULT 0',
    'SELECT ''recycler_prizes.chat_style_id already present, skipping'' AS info');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
