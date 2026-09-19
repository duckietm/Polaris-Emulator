-- Reward track texts.
--
-- The client names a track and its tasks through localization keys
-- (reward_track.<track>.name, reward_track.<track>.task.<task>.desc, ...)
-- that normally live in the client's text files. This table lets the staff
-- editor write them: the emulator sends every text of the active tracks with
-- the tracks themselves and the client registers them before drawing.
-- text_key is the part after "reward_track.<track>.".

CREATE TABLE IF NOT EXISTS `reward_track_texts` (
    `track_id` VARCHAR(64) NOT NULL,
    `text_key` VARCHAR(128) NOT NULL,
    `value` VARCHAR(1000) NOT NULL DEFAULT '',
    PRIMARY KEY (`track_id`, `text_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

INSERT IGNORE INTO `reward_track_texts` (`track_id`, `text_key`, `value`) VALUES
    ('season_1', 'name', 'Season 1'),
    ('season_1', 'desc', 'Chat, visit, decorate and collect the rewards of the season.'),
    ('season_1', 'info', 'Premium doubles the fun: extra prizes on the second row.'),
    ('season_1', 'task.talk.name', 'Chatterbox'),
    ('season_1', 'task.talk.desc', 'Chat with other Habbos.'),
    ('season_1', 'task.visit.name', 'Explorer'),
    ('season_1', 'task.visit.desc', 'Visit rooms built by other Habbos.'),
    ('season_1', 'task.furni.name', 'Decorator'),
    ('season_1', 'task.furni.desc', 'Place furni in your room.'),
    ('season_1', 'task.respect.name', 'Kind soul'),
    ('season_1', 'task.respect.desc', 'Give respect to other Habbos.'),
    ('season_1', 'task.quests.name', 'Adventurer'),
    ('season_1', 'task.quests.desc', 'Complete quests.'),
    ('season_1', 'task.daily.name', 'Regular'),
    ('season_1', 'task.daily.desc', 'Claim your daily tasks.');
