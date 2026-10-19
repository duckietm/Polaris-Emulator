-- The official variable add-on share and import boxes get their interactions.
--
-- `wf_xtra_var_share` (AIR VariableAddonShareAddon, addon code 1003) and
-- `wf_xtra_var_import` (AIR VariableAddonImportAddon, addon code 1004) are now
-- registered as WiredExtraVariableAddonShare / WiredExtraVariableAddonImport.
-- Catalogue dumps that carry the furni have them on `default`, where they fall
-- through to InteractionDefault: they place, open no editor and share nothing.
--
-- Only rows still on `default` are moved, so a hotel that put the furni on an
-- interaction of its own keeps it. `public_name` is operator-visible and stays
-- as the hotel has it. Their settings live in the box's `wired_data` JSON like
-- every other add-on box, so no table changes.
--
-- Charset handling follows V20260923210000: adopted hotels carry latin1 or
-- either utf8mb4 collation on `items_base`, so the comparison converts the
-- `items_base` side and pins the collation explicitly.

CREATE TEMPORARY TABLE `polaris_items_base_variable_addon_share_20261019` (
    `item_identifier` VARCHAR(70) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
    `stale_interaction_type` VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
    `interaction_type` VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
    PRIMARY KEY (`item_identifier`)
) ENGINE=MEMORY;

INSERT INTO `polaris_items_base_variable_addon_share_20261019`
    (`item_identifier`, `stale_interaction_type`, `interaction_type`)
VALUES
    ('wf_xtra_var_share', 'default', 'wf_xtra_var_share'),
    ('wf_xtra_var_import', 'default', 'wf_xtra_var_import');

UPDATE `items_base` AS item
INNER JOIN `polaris_items_base_variable_addon_share_20261019` AS mapping
    ON mapping.`item_identifier`
        = CONVERT(item.`item_name` USING utf8mb4) COLLATE utf8mb4_general_ci
SET item.`interaction_type` = mapping.`interaction_type`
WHERE CONVERT(item.`interaction_type` USING utf8mb4) COLLATE utf8mb4_general_ci
    = mapping.`stale_interaction_type`;

DROP TEMPORARY TABLE `polaris_items_base_variable_addon_share_20261019`;
