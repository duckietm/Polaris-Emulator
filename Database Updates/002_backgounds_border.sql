ALTER TABLE users
ADD COLUMN `background_border_id` INT(11) NOT NULL DEFAULT 0 AFTER `background_id`;

ALTER TABLE infostand_backgrounds
CHANGE COLUMN `category` `category` ENUM('background', 'stand', 'overlay', 'card', 'border') NOT NULL ;


INSERT IGNORE INTO `infostand_backgrounds` (`id`, `category`, `min_rank`, `is_hc_only`, `is_ambassador_only`) VALUES
    (1,  'border', 1, 0, 0),
    (2,  'border', 1, 0, 0),
    (3,  'border', 1, 0, 0),
    (4,  'border', 1, 0, 0),
    (5,  'border', 1, 0, 0),
    (6,  'border', 1, 0, 0),
    (7,  'border', 1, 0, 0),
    (8,  'border', 1, 0, 0),
    (9,  'border', 1, 0, 0),
    (10, 'border', 1, 0, 0),
    (11, 'border', 1, 0, 0),
    (12, 'border', 1, 0, 0),
    (13, 'border', 1, 0, 0),
    (14, 'border', 1, 0, 0),
    (15, 'border', 1, 0, 0),
    (16, 'border', 1, 0, 0),
    (17, 'border', 1, 0, 0),
    (18, 'border', 1, 0, 0),
    (19, 'border', 1, 0, 0),
    (20, 'border', 1, 0, 0),
    (21, 'border', 1, 0, 0),
    (22, 'border', 1, 0, 0),
    (23, 'border', 1, 0, 0),
    (24, 'border', 1, 0, 0),
    (25, 'border', 1, 0, 0);