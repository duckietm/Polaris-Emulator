ALTER TABLE emulator_settings
CHANGE COLUMN `comment` `comment` TEXT NULL DEFAULT '' ;

CREATE TABLE IF NOT EXISTS `password_resets` (
    `user_id`     INT          NOT NULL PRIMARY KEY,
    `token`       VARCHAR(128) NOT NULL,
    `expires_at`  TIMESTAMP    NOT NULL,
    `created_ip`  VARCHAR(64)  NOT NULL DEFAULT '',
    UNIQUE KEY `idx_token` (`token`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO `emulator_settings` (`key`, `value`) VALUES
    ('login.turnstile.enabled',     '0'),
    ('login.turnstile.sitekey',     ''),
    ('login.turnstile.secretkey',   ''),

    ('login.ratelimit.enabled',     '1'),
    ('login.ratelimit.max_attempts','5'),
    ('login.ratelimit.window_sec',  '60'),
    ('login.ratelimit.lockout_sec', '120'),

    ('login.register.enabled',      '1'),
    ('register.max_per_ip',         '5'),
    ('register.default.look',       'hr-100-7.hd-180-1.ch-210-66.lg-270-82.sh-290-80'),
    ('register.default.motto',      'I love Habbo!'),

    ('password.reset.url',          'http://localhost/reset-password'),

    ('smtp.provider',               'own'),
    ('smtp.host',                   'localhost'),
    ('smtp.port',                   '587'),
    ('smtp.username',               ''),
    ('smtp.password',               ''),
    ('smtp.from_address',           'no-reply@example.com'),
    ('smtp.from_name',              'Habbo Hotel'),
    ('smtp.use_tls',                '1'),
    ('smtp.use_ssl',                '0')
ON DUPLICATE KEY UPDATE `value` = VALUES(`value`);

INSERT INTO emulator_settings (`key`, `value`, `comment`) VALUES
  ('new_user_credits',  '0' , 'This is the default setting for habbo credits when creating an account for the NitroV3 Login'),
  ('new_user_duckets',  '0' , 'This is the default setting for habbo duckets when creating an account for the NitroV3 Login'),
  ('new_user_diamonds', '0' , 'This is the default setting for habbo diamonds when creating an account for the NitroV3 Login')
ON DUPLICATE KEY UPDATE `value` = VALUES(`value`);

-- Grant to rank 7 only (adjust rank_7 if your rank id differs)
INSERT INTO `permission_definitions` (`permission_key`, `rank_7`, `comment`) VALUES
  ('cmd_setroom_template', '1', 'Use the setroom_template to copy the room into the template')
ON DUPLICATE KEY UPDATE `rank_7` = VALUES(`rank_7`);

INSERT INTO `emulator_texts` (`key`, `value`) VALUES
  ('commands.description.cmd_setroom_template',   ':setroom_template'),
  ('commands.keys.cmd_setroom_template',           'setroom_template;set_room_template'),
  ('commands.succes.cmd_setroom_template.verify',  'Copy the current room "%roomname%" to room_templates? Type :setroom_template %generic.yes% to confirm.'),
  ('commands.succes.cmd_setroom_template',         'Room saved as template id %id% with %items% items (%skipped% skipped - item_id not in items_base).'),
  ('commands.error.cmd_setroom_template',          'Could not save room as template. Check the server log for details.'),
  ('commands.error.cmd_setroom_template.no_room',  'You must be inside a room to use this command.')
ON DUPLICATE KEY UPDATE `value` = VALUES(`value`);

CREATE TABLE IF NOT EXISTS `room_templates` (
  `template_id` int(11) NOT NULL AUTO_INCREMENT,
  `title` varchar(128) NOT NULL DEFAULT '',
  `description` varchar(256) NOT NULL DEFAULT '',
  `thumbnail` varchar(512) NOT NULL DEFAULT '',
  `sort_order` int(11) NOT NULL DEFAULT 0,
  `enabled` enum('0','1') NOT NULL DEFAULT '1',
  `name` varchar(50) NOT NULL DEFAULT '',
  `room_description` varchar(250) NOT NULL DEFAULT '',
  `model` varchar(100) NOT NULL,
  `password` varchar(50) NOT NULL DEFAULT '',
  `state` enum('open','locked','password','invisible') NOT NULL DEFAULT 'open',
  `users_max` int(11) NOT NULL DEFAULT 25,
  `category` int(11) NOT NULL DEFAULT 0,
  `paper_floor` varchar(50) NOT NULL DEFAULT '0.0',
  `paper_wall` varchar(50) NOT NULL DEFAULT '0.0',
  `paper_landscape` varchar(50) NOT NULL DEFAULT '0.0',
  `thickness_wall` int(11) NOT NULL DEFAULT 0,
  `thickness_floor` int(11) NOT NULL DEFAULT 0,
  `moodlight_data` varchar(2048) NOT NULL DEFAULT '',
  `override_model` enum('0','1') NOT NULL DEFAULT '0',
  `trade_mode` int(2) NOT NULL DEFAULT 2,
  `heightmap` mediumtext NOT NULL DEFAULT '',
  `door_x` int(11) NOT NULL DEFAULT 0,
  `door_y` int(11) NOT NULL DEFAULT 0,
  `door_dir` int(4) NOT NULL DEFAULT 2,
  PRIMARY KEY (`template_id`),
  KEY `enabled_sort` (`enabled`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC;

-- --------------------------------------------------------
-- Items belonging to a template. Clone target is `items`.
-- `template_id` replaces `room_id`; `user_id` is absent because items
-- are re-owned by the new user at clone time.
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS `room_templates_items` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `template_id` int(11) NOT NULL,
  `item_id` int(11) unsigned NOT NULL,
  `wall_pos` varchar(20) NOT NULL DEFAULT '',
  `x` int(11) NOT NULL DEFAULT 0,
  `y` int(11) NOT NULL DEFAULT 0,
  `z` double(10,6) NOT NULL DEFAULT 0.000000,
  `rot` int(11) NOT NULL DEFAULT 0,
  `extra_data` varchar(2096) NOT NULL DEFAULT '',
  `wired_data` varchar(4096) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `template_id` (`template_id`),
  CONSTRAINT `fk_rt_items_template`
    FOREIGN KEY (`template_id`) REFERENCES `room_templates` (`template_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_rt_items_item_base`
    FOREIGN KEY (`item_id`) REFERENCES `items_base` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE IF NOT EXISTS `users_remember_families` (
  `family_id`       char(36) NOT NULL,
  `user_id`         int(11)  NOT NULL,
  `current_version` int(11)  NOT NULL DEFAULT 1,
  `created_at`      int(11)  NOT NULL,
  `expires_at`      int(11)  NOT NULL,
  `revoked`         tinyint(1) NOT NULL DEFAULT 0,
  `last_ip`         varchar(45) NOT NULL DEFAULT '',
  PRIMARY KEY (`family_id`),
  KEY `user_id`    (`user_id`),
  KEY `expires_at` (`expires_at`),
  CONSTRAINT `fk_remember_family_user`
    FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC;

DROP TABLE IF EXISTS `users_remember_tokens`;

INSERT INTO `emulator_settings` (`key`, `value`) VALUES
  ('login.remember.duration.days',         '30'),
  ('login.remember.rotate.interval.minutes', '15'),
  ('login.remember.jwt.secret',            '')
ON DUPLICATE KEY UPDATE `value` = `value`;

