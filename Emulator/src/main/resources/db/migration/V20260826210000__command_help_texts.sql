-- Descriptions for the ":commands" listing.
--
-- commands.description.<permission> holds the usage line - it starts with ':' and
-- replaces the plain command name - so there was nowhere to say what a command
-- actually does. On a stock database 112 of the listed commands showed a usage
-- line and no description at all.
--
-- permission_definitions.comment already carries that sentence for every command,
-- written for the permission editor. This copies it into the texts table under a
-- new commands.help.<permission> key: user-facing copy belongs there, and hotel
-- owners can reword or translate it without touching permissions.

-- Collected first so the insert never selects from the table it writes to.
CREATE TEMPORARY TABLE `command_help_seed` (
    `key` VARCHAR(100) NOT NULL,
    `value` VARCHAR(4096) NOT NULL,
    PRIMARY KEY (`key`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- The join against commands.keys.* keeps this to permissions that really are
-- commands, rather than every permission in the table.
INSERT INTO `command_help_seed` (`key`, `value`)
SELECT CONCAT('commands.help.', `definition`.`permission_key`), TRIM(`definition`.`comment`)
FROM `permission_definitions` AS `definition`
JOIN `emulator_texts` AS `command_key`
    ON `command_key`.`key` = CONCAT('commands.keys.', `definition`.`permission_key`)
WHERE `definition`.`comment` IS NOT NULL
    AND TRIM(`definition`.`comment`) <> '';

-- Keeping the existing value on duplicate means a reworded help text survives.
INSERT INTO `emulator_texts` (`key`, `value`)
SELECT `key`, `value` FROM `command_help_seed`
ON DUPLICATE KEY UPDATE `value` = `emulator_texts`.`value`;

DROP TEMPORARY TABLE `command_help_seed`;

-- Two commands never had a usage line, so they listed as a bare command name.
INSERT INTO `emulator_texts` (`key`, `value`) VALUES
    ('commands.description.cmd_softkick', ':softkick <username>'),
    ('commands.description.cmd_subscription', ':subscription <username> <subscription> <add|remove> [seconds]')
ON DUPLICATE KEY UPDATE `value` = `emulator_texts`.`value`;
