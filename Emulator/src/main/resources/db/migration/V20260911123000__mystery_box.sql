-- Mystery box: a box and a key of the same colour, held by two different
-- players, opened together for a prize each.
--
-- The prizes are catalog items, so a hotel builds the pool the way it builds
-- everything else it sells: make the items in the catalog, then list them here.
-- Whatever the catalog item contains is what the winner receives, bundles and
-- all.
--   colour   which boxes this prize belongs to; empty means every colour
--   weight   how likely it is, relative to the other prizes of that colour
CREATE TABLE IF NOT EXISTS `mystery_box_prizes` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `catalog_item_id` INT NOT NULL,
    `colour` VARCHAR(32) NOT NULL DEFAULT '',
    `enabled` TINYINT(1) NOT NULL DEFAULT 1,
    `weight` INT NOT NULL DEFAULT 1,
    PRIMARY KEY (`id`),
    KEY `colour_enabled` (`colour`, `enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
