CREATE TABLE IF NOT EXISTS `habbo_mentions` (
    `id`               INT(11)       NOT NULL AUTO_INCREMENT,
    `target_user_id`   INT(11)       NOT NULL,
    `sender_user_id`   INT(11)       NOT NULL,
    `sender_username`  VARCHAR(64)   NOT NULL DEFAULT '',
    `room_id`          INT(11)       NOT NULL DEFAULT 0,
    `room_name`        VARCHAR(64)   NOT NULL DEFAULT '',
    `message`          VARCHAR(255)  NOT NULL DEFAULT '',
    `mention_type`     TINYINT(1)    NOT NULL DEFAULT 0  COMMENT '0 = direct (@nick), 1 = broadcast (@all/@friends/@room)',
    `timestamp`        INT(11)       NOT NULL DEFAULT 0,
    `read`             TINYINT(1)    NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_target_id`        (`target_user_id`, `id`),
    KEY `idx_target_unread`    (`target_user_id`, `read`),
    KEY `idx_target_timestamp` (`target_user_id`, `timestamp`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;




INSERT INTO `permission_definitions`
    (`permission_key`, `max_value`, `comment`,
     `rank_1`, `rank_2`, `rank_3`, `rank_4`, `rank_5`, `rank_6`, `rank_7`, `rank_8`)
VALUES
    ('acc_mention_everyone', 1,
     'Allow sending @all / @everyone / @tutti broadcast mentions (hotel-wide).',
     0, 0, 0, 0, 1, 1, 1, 1),
    ('acc_mention_friends', 1,
     'Allow sending @friends / @amici broadcast mentions (sender''s online buddies).',
     0, 0, 0, 0, 1, 1, 1, 1),
    ('cmd_disablementions', 1,
     'Allow toggling :disablementions to stop receiving any @mention notifications.',
     1, 1, 1, 1, 1, 1, 1, 1),
    ('cmd_disablemassmentions', 1,
     'Allow toggling :disablemassmentions to stop receiving broadcast mentions (direct @nick still works).',
     1, 1, 1, 1, 1, 1, 1, 1)
ON DUPLICATE KEY UPDATE
    `comment` = VALUES(`comment`);


-- ----------------------------------------------------------------------------
-- 3. Emulator settings: cooldowns, caps and alias lists
--
--    Only inserted when missing - existing tuned values are preserved.
-- ----------------------------------------------------------------------------

INSERT IGNORE INTO `emulator_settings` (`key`, `value`, `comment`) VALUES
    ('mentions.enabled',          '1',
        'Master switch. 1 = process @mentions, 0 = disable the feature entirely.'),
    ('mentions.max.targets',      '50',
        'Hard cap on how many users a single broadcast (@all / @friends / @room) can fan out to.'),
    ('mentions.cooldown.ms',      '3000',
        'Per-sender cooldown between any two mentions, in milliseconds.'),
    ('mentions.room.cooldown.ms', '15000',
        'Extra per-sender cooldown for broadcast mentions (@all / @friends / @room) on top of mentions.cooldown.ms.'),
    ('mentions.store.limit',      '50',
        'Number of mentions returned in the initial RequestMentionsList response.'),
    ('mentions.request.cooldown.ms', '2000',
        'Per-user cooldown between RequestMentionsList packets.'),
    ('mentions.markread.cooldown.ms', '500',
        'Per-user cooldown between mark-single-as-read packets.'),
    ('mentions.markall.cooldown.ms',  '5000',
        'Per-user cooldown between mark-all-as-read packets (bulk DB update).'),
    ('mentions.delete.cooldown.ms',   '500',
        'Per-user cooldown between delete-mention packets.'),
    ('mentions.everyone.aliases', 'all,everyone,tutti',
        'Comma-separated aliases that trigger an @everyone broadcast (requires acc_mention_everyone).'),
    ('mentions.friends.aliases',  'friends,amici',
        'Comma-separated aliases that trigger an @friends broadcast (requires acc_mention_friends).'),
    ('mentions.room.aliases',     'room,stanza',
        'Comma-separated aliases that trigger an @room broadcast (no permission required, room scope only).');


ALTER TABLE `wordfilter`
ADD COLUMN `prefix_only` ENUM('0','1') NOT NULL DEFAULT '0'
COMMENT 'When 1, this word only applies to custom prefixes, not to chat/motto/guild.' AFTER `mute`;

-- ----------------------------------------------------------------------------
-- 5. Per-user mention preferences (:disablementions / :disablemassmentions)
--
--    Read by HabboStats (default '1' = enabled), toggled by the commands.
--    Without these columns the toggle commands cannot persist.
-- ----------------------------------------------------------------------------

ALTER TABLE `users_settings`
    ADD COLUMN IF NOT EXISTS `mentions_enabled`      ENUM('0','1') NOT NULL DEFAULT '1'
        COMMENT 'Receive @nick mention notifications.',
    ADD COLUMN IF NOT EXISTS `mass_mentions_enabled` ENUM('0','1') NOT NULL DEFAULT '1'
        COMMENT 'Receive broadcast (@all / @friends / @room) mentions.';
