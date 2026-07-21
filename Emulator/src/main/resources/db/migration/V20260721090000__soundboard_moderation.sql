ALTER TABLE `permission_ranks`
    ADD COLUMN IF NOT EXISTS `soundboard_cooldown_seconds` INT NOT NULL DEFAULT 60;

ALTER TABLE `permissions`
    ADD COLUMN IF NOT EXISTS `soundboard_cooldown_seconds` INT NOT NULL DEFAULT 60;

ALTER TABLE `soundboard_sounds`
    ADD COLUMN IF NOT EXISTS `min_rank` INT NOT NULL DEFAULT 1;
