-- The room linker furni gets its own interaction.
--
-- `wf_room_linker` is the furni the teleport-to-room wired effect leads users
-- through: it is bought in pairs like a teleporter, one is placed in each room,
-- and the effect sends users to the room the pair stands in. Until now no
-- interaction existed for it. Hotels that imported a recent catalog carry it
-- on `teletile`, which is not a registered interaction key (the walk-on
-- teleport is `teleporttile`), and the others on `default`; both fall through
-- to InteractionDefault silently, so the furni was bought singly, the effect
-- refused it, and nothing reported the mismatch.
--
-- Pairs bought while the row was on a teleporter key keep their
-- `items_teleports` rows and become working linkers with no data change.
--
-- Only `interaction_type` is reconciled, and only where it diverges, so a hotel
-- that already holds the right value is left untouched. `public_name` is
-- operator-visible and stays as the hotel has it.
--
-- Charset handling follows V20260905120000: adopted hotels carry latin1 or
-- either utf8mb4 collation on `items_base`, so the comparison converts the
-- `items_base` side and pins the collation explicitly.

CREATE TEMPORARY TABLE `polaris_items_base_room_linker_repair_20260923` (
    `item_identifier` VARCHAR(70) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
    `interaction_type` VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
    PRIMARY KEY (`item_identifier`)
) ENGINE=MEMORY;

INSERT INTO `polaris_items_base_room_linker_repair_20260923`
    (`item_identifier`, `interaction_type`)
VALUES
    ('wf_room_linker', 'wf_room_linker');

UPDATE `items_base` AS item
INNER JOIN `polaris_items_base_room_linker_repair_20260923` AS mapping
    ON mapping.`item_identifier`
        = CONVERT(item.`item_name` USING utf8mb4) COLLATE utf8mb4_general_ci
SET item.`interaction_type` = mapping.`interaction_type`
WHERE CONVERT(item.`interaction_type` USING utf8mb4) COLLATE utf8mb4_general_ci
    <> mapping.`interaction_type`;

DROP TEMPORARY TABLE `polaris_items_base_room_linker_repair_20260923`;
