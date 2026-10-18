-- WIRED Link (AIR WiredLinkController): networks a user names, and the saved settings of each Link
-- furni (`wf_link`). A network belongs to its owner; only Links that owner owns join it, one per room
-- (the unique key; a NULL network_id is "not connected" and does not count). Deleting a network
-- disconnects its Links in code, so no foreign key is needed and the tables stay independent of the
-- `items` engine.
CREATE TABLE IF NOT EXISTS `wired_link_networks` (
    `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
    `owner_id` INT NOT NULL,
    `name` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
    `created_at` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_wired_link_networks_owner` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `wired_link_endpoints` (
    `item_id` INT NOT NULL,
    `network_id` INT UNSIGNED NULL DEFAULT NULL,
    `room_id` INT NOT NULL,
    `owner_id` INT NOT NULL,
    `receive_local` TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (`item_id`),
    UNIQUE KEY `uniq_wired_link_endpoints_network_room` (`network_id`, `room_id`),
    KEY `idx_wired_link_endpoints_owner` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- The official furni get their interactions. Catalogue dumps that carry them have them on `default`,
-- where they place but do nothing. Only rows still on `default` move, so a hotel that put the furni
-- on an interaction of its own keeps it. Charset handling follows V20261017190000.
CREATE TEMPORARY TABLE `polaris_items_base_wired_link_20261017` (
    `item_identifier` VARCHAR(70) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
    `stale_interaction_type` VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
    `interaction_type` VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
    PRIMARY KEY (`item_identifier`)
) ENGINE=MEMORY;

INSERT INTO `polaris_items_base_wired_link_20261017`
    (`item_identifier`, `stale_interaction_type`, `interaction_type`)
VALUES
    ('wf_link', 'default', 'wf_link'),
    ('wf_act_send_message', 'default', 'wf_act_send_message'),
    ('wf_act_send_dataframe', 'default', 'wf_act_send_dataframe'),
    ('wf_trg_receive_message', 'default', 'wf_trg_receive_message'),
    ('wf_trg_receive_dataframe', 'default', 'wf_trg_receive_dataframe'),
    ('wf_xtra_link_speaker', 'default', 'wf_xtra_link_speaker');

UPDATE `items_base` AS item
INNER JOIN `polaris_items_base_wired_link_20261017` AS mapping
    ON mapping.`item_identifier`
        = CONVERT(item.`item_name` USING utf8mb4) COLLATE utf8mb4_general_ci
SET item.`interaction_type` = mapping.`interaction_type`
WHERE CONVERT(item.`interaction_type` USING utf8mb4) COLLATE utf8mb4_general_ci
    = mapping.`stale_interaction_type`;

DROP TEMPORARY TABLE `polaris_items_base_wired_link_20261017`;
