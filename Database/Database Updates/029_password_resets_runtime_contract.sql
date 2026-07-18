-- Bring legacy password_resets tables up to the columns used by SessionEndpoints.
-- Existing rows are preserved; NULL user ids remain valid until replaced by a
-- new reset request, while new runtime writes always provide user_id.

ALTER TABLE `password_resets`
    ADD COLUMN IF NOT EXISTS `user_id` INT(11) NULL FIRST,
    ADD COLUMN IF NOT EXISTS `created_ip` VARCHAR(64) NOT NULL DEFAULT '';

ALTER TABLE `password_resets`
    ADD UNIQUE INDEX IF NOT EXISTS `idx_password_resets_user_id` (`user_id`);
