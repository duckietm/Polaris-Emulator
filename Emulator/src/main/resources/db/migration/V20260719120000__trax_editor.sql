-- Trax editor: users compose their own soundtracks, bought with a configurable
-- currency, stored in the shared soundtracks table so the existing jukebox
-- pipeline (music discs, trax_playlist, TraxManager) plays them unchanged.

CREATE TABLE IF NOT EXISTS `users_soundtracks` (
    `id` INT(11) NOT NULL AUTO_INCREMENT,
    `user_id` INT(11) NOT NULL,
    `soundtrack_id` INT(11) NOT NULL,
    `created_at` INT(11) NOT NULL DEFAULT 0,
    `updated_at` INT(11) NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `idx_users_soundtracks_soundtrack` (`soundtrack_id`),
    KEY `idx_users_soundtracks_user_id` (`user_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;

-- Operator-owned settings; inserted only when missing so tuned values survive.
-- trax.editor.song.cost.currency: -1 = credits, otherwise a users_currency/seasonal
-- type (0 = duckets, 5 = diamonds by default hotel config).
INSERT IGNORE INTO `emulator_settings` (`key`, `value`, `comment`) VALUES
    ('trax.editor.enabled', '1',
        'Master switch for the Trax song editor. 1 = users can compose songs, 0 = feature off.'),
    ('trax.editor.max_songs', '5',
        'How many own songs a user may own at once.'),
    ('trax.editor.song.cost.currency', '5',
        'Currency charged for a new song: -1 = credits, otherwise a users_currency type (0 = duckets, 5 = diamonds).'),
    ('trax.editor.song.cost.amount', '25',
        'Price of one new song in the configured currency. 0 = free.'),
    ('trax.editor.disk.base_item', '0',
        'items_base id used for the music disc delivered with a new song. 0 = first base item with interaction_type musicdisc.');
