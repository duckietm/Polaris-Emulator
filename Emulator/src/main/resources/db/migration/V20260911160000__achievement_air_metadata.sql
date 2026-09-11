-- AIR achievement states: disabled, enabled, archived, off-season, wired-controlled.
ALTER TABLE achievements
    ADD COLUMN state SMALLINT NOT NULL DEFAULT 1,
    ADD COLUMN display_method INT NOT NULL DEFAULT 0,
    ADD COLUMN subcategory VARCHAR(64) NOT NULL DEFAULT '',
    MODIFY COLUMN category ENUM('identity','explore','music','social','games','room_builder','pets','tools','events','other','test','invisible','misc','archive','wired_games') NOT NULL DEFAULT 'identity';

-- Legacy seeds used MyISAM; progress and queued events must participate in reward transactions.
ALTER TABLE users_achievements ENGINE = InnoDB, ROW_FORMAT = DYNAMIC;
ALTER TABLE users_achievements_queue ENGINE = InnoDB, ROW_FORMAT = DYNAMIC;
