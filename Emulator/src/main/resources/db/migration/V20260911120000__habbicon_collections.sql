CREATE TABLE habbicon_collections (
    id INT NOT NULL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    reward_id INT NOT NULL DEFAULT 0,
    cost_credits INT UNSIGNED NOT NULL DEFAULT 0,
    cost_points INT UNSIGNED NOT NULL DEFAULT 0,
    points_type INT UNSIGNED NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE habbicons (
    id INT NOT NULL PRIMARY KEY,
    collection_id INT NOT NULL,
    name VARCHAR(100) NOT NULL,
    cost_credits INT UNSIGNED NOT NULL DEFAULT 0,
    cost_points INT UNSIGNED NOT NULL DEFAULT 0,
    points_type INT UNSIGNED NOT NULL DEFAULT 0,
    available BOOLEAN NOT NULL DEFAULT TRUE,
    default_owned BOOLEAN NOT NULL DEFAULT FALSE,
    KEY collection_id (collection_id),
    FOREIGN KEY (collection_id) REFERENCES habbicon_collections(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE users_habbicons (
    user_id INT NOT NULL,
    habbicon_id INT NOT NULL,
    state TINYINT NOT NULL DEFAULT 2,
    unseen BOOLEAN NOT NULL DEFAULT FALSE,
    last_used BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, habbicon_id),
    KEY recent (user_id, last_used),
    FOREIGN KEY (habbicon_id) REFERENCES habbicons(id),
    CHECK (state IN (1, 2, 3))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Collection membership and rewards are hotel configuration; AIR supplies them over the wire.
-- Names and collection badge ids come from the September Habbicon asset set.
INSERT INTO habbicon_collections (id, name, reward_id) VALUES
    (7, 'duck', 38), (8, 'duck2', 49), (5, 'frank', 60), (6, 'toast', 71);

INSERT INTO habbicons (id, collection_id, name, default_owned) VALUES
    (28, 7, 'duck_duck', 1),
    (29, 7, 'duck_happy', 1),
    (30, 7, 'duck_sad', 1),
    (31, 7, 'duck_shock', 1),
    (32, 7, 'duck_think', 1),
    (33, 7, 'duck_nohear', 1),
    (34, 7, 'duck_nosee', 1),
    (35, 7, 'duck_nosay', 1),
    (36, 7, 'duck_angel', 1),
    (37, 7, 'duck_devil', 1),
    (38, 7, 'duck_spinning', 1),
    (39, 8, 'duck_cool', 0),
    (40, 8, 'duck_pleased', 0),
    (41, 8, 'duck_laughing', 0),
    (42, 8, 'duck_grimace', 0),
    (43, 8, 'duck_devious', 0),
    (44, 8, 'duck_metal', 0),
    (45, 8, 'duck_pleading', 0),
    (46, 8, 'duck_silly', 0),
    (47, 8, 'duck_wink', 0),
    (48, 8, 'duck_party', 0),
    (49, 8, 'duck_love', 0),
    (50, 5, 'frank_frank', 0),
    (51, 5, 'frank_smile', 0),
    (52, 5, 'frank_happy', 0),
    (53, 5, 'frank_sad', 0),
    (54, 5, 'frank_scared', 0),
    (55, 5, 'frank_surprised', 0),
    (56, 5, 'frank_thinking', 0),
    (57, 5, 'frank_silly', 0),
    (58, 5, 'frank_relief', 0),
    (59, 5, 'frank_wink', 0),
    (60, 5, 'frank_stareyes', 0),
    (61, 6, 'toast_toast', 0),
    (62, 6, 'toast_happy', 0),
    (63, 6, 'toast_cute', 0),
    (64, 6, 'toast_wink', 0),
    (65, 6, 'toast_sad', 0),
    (66, 6, 'toast_cry', 0),
    (67, 6, 'toast_grumpy', 0),
    (68, 6, 'toast_sleep', 0),
    (69, 6, 'toast_shock', 0),
    (70, 6, 'toast_flustered', 0),
    (71, 6, 'toast_fine', 0);

-- Preserve access for users who could already use every Habbicon before ownership existed.
INSERT INTO users_habbicons (user_id, habbicon_id)
SELECT users.id, habbicons.id FROM users CROSS JOIN habbicons;

ALTER TABLE catalog_items ADD COLUMN habbicon_id INT NOT NULL DEFAULT 0;
