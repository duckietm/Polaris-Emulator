-- Wired chest transactions as the official client lists them: one line per action, however many chests it
-- touched, with the method that moved it (manual, wired, contract payment / reward / trade) and its extra info.
--
-- The table keeps its row per chest. transaction_key ties the rows of one action together (0 = a row on its
-- own, which is every row written before this migration); method is -1 for those and is then read from
-- source (player = manual, wired = wired).
--
-- Idempotent: safe to replay.

ALTER TABLE `wired_chest_transactions`
    ADD COLUMN IF NOT EXISTS `transaction_key` BIGINT(20) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS `method` TINYINT(4) NOT NULL DEFAULT -1,
    ADD COLUMN IF NOT EXISTS `definition_info` VARCHAR(255) NOT NULL DEFAULT '';

CREATE INDEX IF NOT EXISTS `idx_wired_chest_transactions_key`
    ON `wired_chest_transactions` (`room_id`, `transaction_key`);
