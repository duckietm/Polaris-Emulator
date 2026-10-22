-- Group badge images drawn by the emulator (BadgeImager): writes <badge>.png to imager.location.output.badges on
-- one background thread for the client to load through group.badge.url. Every statement is idempotent.

INSERT INTO `emulator_settings` (`key`, `value`, `comment`) VALUES
    ('imager.badges.queue.size', '500', 'Badge imager: badge images that may wait to be written; more are dropped and the client draws those itself.'),
    ('imager.badges.backfill', '1', 'Badge imager: after loading the badge parts (boot, :update_guildparts) draw every guild badge that has no image yet.'),
    ('hotel.guilds.badge.change.cooldown.seconds', '3', 'Seconds a guild must wait between two badge changes (0 = no wait).'),
    ('hotel.guilds.badge.validate_parts', '1', 'Only accept guild badges made of parts and colours in guilds_elements (0 = only check the code shape).')
ON DUPLICATE KEY UPDATE `value` = `value`;
