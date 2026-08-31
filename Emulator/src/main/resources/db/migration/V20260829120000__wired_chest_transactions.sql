-- Room-wide transaction log for wired storage chests.
--
-- Each chest already keeps its own rolling log of the last 50 rows inside
-- items.wired_data, which is enough for the single-chest window but not for the
-- room-level view: that log dies with the furni when it is picked up, cannot be
-- paginated, and carries no per-transaction detail. This table is the durable
-- room-scoped record the chests tab reads.
--
-- transaction_type: 0 = deposit, 1 = withdraw.
-- source:           0 = a player acting through the chest window, 1 = wired.
-- chest_kind:       0 = currency chest, 1 = furni chest (mirrors ChestStorage).
-- currency_type:    the currency id for a currency chest, -1 for a furni chest.
-- details:          compact JSON with the furni involved, read only by the
--                   detail window; NULL for currency rows, which need none.
--
-- Idempotent by design: hotel owners sometimes replay a schema patch.

CREATE TABLE IF NOT EXISTS `wired_chest_transactions` (
    `id` INT(11) NOT NULL AUTO_INCREMENT,
    `room_id` INT(11) NOT NULL,
    `chest_id` INT(11) NOT NULL,
    `chest_kind` TINYINT(4) NOT NULL DEFAULT 0,
    `transaction_type` TINYINT(4) NOT NULL DEFAULT 0,
    `source` TINYINT(4) NOT NULL DEFAULT 0,
    `user_id` INT(11) NOT NULL DEFAULT 0,
    `user_name` VARCHAR(64) NOT NULL DEFAULT '',
    `currency_type` INT(11) NOT NULL DEFAULT -1,
    `withdrawn` INT(11) NOT NULL DEFAULT 0,
    `deposited` INT(11) NOT NULL DEFAULT 0,
    `details` TEXT DEFAULT NULL,
    `timestamp` INT(11) NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_wired_chest_transactions_room` (`room_id`, `id`),
    KEY `idx_wired_chest_transactions_chest` (`chest_id`),
    KEY `idx_wired_chest_transactions_prune` (`room_id`, `timestamp`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci;
