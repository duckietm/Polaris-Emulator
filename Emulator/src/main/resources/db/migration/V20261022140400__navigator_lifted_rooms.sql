-- Navigator "lifted" rooms (AIR LiftDataContainer, NavigatorLifted packet): promoted rooms the
-- navigator shows above the hotel view results with a teaser image. `image` is a path below the
-- client's image.library.url (empty = the client's default teaser, officialrooms_hq/nav_teaser_wl.png);
-- an empty `caption` falls back to the room name. Rows show in `order_num` order; reload them with
-- :update_navigator or the housekeeping navigator reload. Empty by default, so nothing changes until a
-- hotel fills it.
CREATE TABLE IF NOT EXISTS `navigator_lifted_rooms` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `room_id` INT NOT NULL,
    `area_id` INT NOT NULL DEFAULT 0,
    `image` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '',
    `caption` VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '',
    `order_num` INT NOT NULL DEFAULT 0,
    `visible` ENUM('0', '1') NOT NULL DEFAULT '1',
    PRIMARY KEY (`id`),
    KEY `idx_navigator_lifted_rooms_order` (`visible`, `order_num`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
