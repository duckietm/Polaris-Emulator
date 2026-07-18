ALTER TABLE `logs_economy`
    ADD COLUMN IF NOT EXISTS `actor_id` INT UNSIGNED NULL AFTER `user_id`,
    ADD COLUMN IF NOT EXISTS `reason` VARCHAR(96) NOT NULL DEFAULT 'legacy.unspecified' AFTER `operation`,
    ADD INDEX IF NOT EXISTS `idx_logs_economy_actor_created` (`actor_id`, `created_at`),
    ADD INDEX IF NOT EXISTS `idx_logs_economy_reason_created` (`reason`, `created_at`);

DROP TRIGGER IF EXISTS `logs_economy_immutable_update`;
CREATE TRIGGER `logs_economy_immutable_update`
    BEFORE UPDATE ON `logs_economy`
    FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'logs_economy is append-only';

DROP TRIGGER IF EXISTS `logs_economy_immutable_delete`;
CREATE TRIGGER `logs_economy_immutable_delete`
    BEFORE DELETE ON `logs_economy`
    FOR EACH ROW
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'logs_economy is append-only';
