ALTER TABLE users
ADD COLUMN `last_username_change` INT(11) NOT NULL;

INSERT INTO emulator_settings (`key`, `value`, `comment`)
VALUES ('rename.cooldown_days', '30', 'Days between username changes');