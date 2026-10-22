-- Friend bar activity (AIR FriendToolbarNotification, HabboFriendBarData.makeNotification): online friends
-- see on the friend bar a room event the user started, an achievement level, a completed quest, or the game
-- they are playing. Each kind has its own switch. Idempotent.

INSERT INTO `emulator_settings` (`key`, `value`, `comment`) VALUES
    ('hotel.friendbar.activity.enabled', '1', 'Friend bar activity: tell online friends about room events, achievements, quests and games (1 = on, 0 = off).'),
    ('hotel.friendbar.activity.room_events', '1', 'Friend bar activity: a room event (promotion) the user starts.'),
    ('hotel.friendbar.activity.achievements', '1', 'Friend bar activity: an achievement level the user unlocks.'),
    ('hotel.friendbar.activity.quests', '1', 'Friend bar activity: a quest the user completes.'),
    ('hotel.friendbar.activity.games', '1', 'Friend bar activity: the user is playing a game (SnowStorm) until it ends.')
ON DUPLICATE KEY UPDATE `value` = `value`;
