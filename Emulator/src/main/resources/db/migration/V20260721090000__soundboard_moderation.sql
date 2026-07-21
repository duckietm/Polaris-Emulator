ALTER TABLE `permission_ranks`
    ADD COLUMN IF NOT EXISTS `soundboard_cooldown_seconds` INT NOT NULL DEFAULT 60;

ALTER TABLE `permissions`
    ADD COLUMN IF NOT EXISTS `soundboard_cooldown_seconds` INT NOT NULL DEFAULT 60;

ALTER TABLE `soundboard_sounds`
    ADD COLUMN IF NOT EXISTS `min_rank` INT NOT NULL DEFAULT 1;

INSERT INTO `soundboard_sounds` (`name`, `url`, `enabled`, `sort_order`, `min_rank`)
SELECT 'Campanella', '/sounds/soundboard/campanella.mp3', 1, 10, 1
WHERE NOT EXISTS (
    SELECT 1 FROM `soundboard_sounds`
    WHERE `url` = '/sounds/soundboard/campanella.mp3'
);

INSERT INTO `soundboard_sounds` (`name`, `url`, `enabled`, `sort_order`, `min_rank`)
SELECT 'Applauso', '/sounds/soundboard/applauso.mp3', 1, 20, 1
WHERE NOT EXISTS (
    SELECT 1 FROM `soundboard_sounds`
    WHERE `url` = '/sounds/soundboard/applauso.mp3'
);

INSERT IGNORE INTO `emulator_texts` (`key`, `value`) VALUES
    ('soundboard.cooldown.remaining', 'Attendi %seconds%s prima di riprodurre un altro suono.');
