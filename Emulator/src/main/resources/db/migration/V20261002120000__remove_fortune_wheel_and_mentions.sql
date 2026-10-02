DROP TABLE IF EXISTS `wheel_prizes`, `wheel_user_state`, `wheel_recent_wins`, `habbo_mentions`;

DELETE FROM `emulator_settings`
WHERE `key` IN (
    'wheel.free_spins_per_day',
    'wheel.spin_cost',
    'wheel.spin_cost_type'
)
OR `key` LIKE 'mentions.%';

DELETE FROM `emulator_texts`
WHERE `key` IN (
    'commands.description.cmd_disablementions',
    'commands.description.cmd_disablemassmentions',
    'bubblealerts.notif_mention.message'
);

DELETE FROM `permission_definitions`
WHERE `permission_key` IN (
    'acc_wheeladmin',
    'acc_mention_everyone',
    'acc_mention_friends',
    'cmd_disablementions',
    'cmd_disablemassmentions'
);

ALTER TABLE `users_settings`
    DROP COLUMN IF EXISTS `mentions_enabled`,
    DROP COLUMN IF EXISTS `mass_mentions_enabled`;
