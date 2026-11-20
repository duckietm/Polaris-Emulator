-- Profile window customisation: a banner picked from a gallery and an accent colour from a palette.
ALTER TABLE users
ADD COLUMN IF NOT EXISTS `profile_banner_id` INT(11) NOT NULL DEFAULT 0 AFTER `background_border_id`,
ADD COLUMN IF NOT EXISTS `profile_accent_id` INT(11) NOT NULL DEFAULT 0 AFTER `profile_banner_id`;

SET @background_category_type := (
    SELECT COLUMN_TYPE
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'infostand_backgrounds'
      AND COLUMN_NAME = 'category'
);
SET @background_category_ddl := CASE
    WHEN @background_category_type = 'enum(''background'',''stand'',''overlay'',''card'',''border'',''banner'',''accent'')'
        THEN 'DO 0'
    WHEN @background_category_type = 'enum(''background'',''stand'',''overlay'',''card'',''border'')'
        THEN 'ALTER TABLE `infostand_backgrounds` MODIFY COLUMN `category` ENUM(''background'',''stand'',''overlay'',''card'',''border'',''banner'',''accent'') NOT NULL'
    ELSE 'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''infostand_backgrounds.category has an unknown custom definition; add banner and accent without removing custom values, then retry'''
END;
PREPARE background_category_stmt FROM @background_category_ddl;
EXECUTE background_category_stmt;
DEALLOCATE PREPARE background_category_stmt;

INSERT IGNORE INTO `infostand_backgrounds` (`id`, `category`, `min_rank`, `is_hc_only`, `is_ambassador_only`) VALUES
    (1,  'banner', 1, 0, 0),
    (2,  'banner', 1, 0, 0),
    (3,  'banner', 1, 0, 0),
    (4,  'banner', 1, 0, 0),
    (5,  'banner', 1, 0, 0),
    (6,  'banner', 1, 0, 0),
    (7,  'banner', 1, 0, 0),
    (8,  'banner', 1, 0, 0),
    (1,  'accent', 1, 0, 0),
    (2,  'accent', 1, 0, 0),
    (3,  'accent', 1, 0, 0),
    (4,  'accent', 1, 0, 0),
    (5,  'accent', 1, 0, 0),
    (6,  'accent', 1, 0, 0),
    (7,  'accent', 1, 0, 0),
    (8,  'accent', 1, 0, 0),
    (9,  'accent', 1, 0, 0),
    (10, 'accent', 1, 0, 0),
    (11, 'accent', 1, 0, 0),
    (12, 'accent', 1, 0, 0);
