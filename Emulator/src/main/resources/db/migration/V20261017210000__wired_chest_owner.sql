-- Wired chests record their owner.
--
-- When a chest item is deleted, what it holds goes back to its owner first
-- (ChestRelease). If an item ever disappears without that (a plugin, a manual
-- query, a path that did not release it), the chest's rows are left without
-- an item, and the owner is the one thing the rows themselves do not say:
-- stored furni are parked without a user_id. owner_id keeps it, so the
-- startup sweep (ChestRelease.sweepOrphans) can still hand the contents back.
-- Chests whose item is already gone keep 0 here; the sweep keeps their rows
-- and logs them rather than dropping anything.
--
-- The server refreshes owner_id whenever a chest is created or saved, and the
-- sweep refreshes it for every chest whose item exists on start.
--
-- Idempotent by design: hotel owners sometimes replay a schema patch.

ALTER TABLE `wired_chests`
    ADD COLUMN IF NOT EXISTS `owner_id` INT(11) NOT NULL DEFAULT 0 AFTER `item_id`;

UPDATE `wired_chests` c
    JOIN `items` i ON i.`id` = c.`item_id`
SET c.`owner_id` = i.`user_id`
WHERE i.`user_id` > 0;
