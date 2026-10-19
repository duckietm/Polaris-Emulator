-- Habbo's own classnames for wired boxes we already have under another name.
--
-- Habbo's furnidata names these boxes differently from our interaction types:
--   wf_act_progress_ach          -> wf_act_progress_achievement (Progress Achievement)
--   wf_act_set_visibility        -> wf_act_change_opacity       (Set Furni Transparency, official 61)
--   wf_trg_room_initializes      -> wf_trg_room_initialized     (Room Initialized)
--   wf_var_dailytask             -> wf_var_daily_task           (Daily Task)
-- and keeps three "old WIRED" prototypes of standard boxes:
--   wf_ltdproto_act_toggle_state -> wf_act_toggle_state
--   wf_proto_cnd_trggrer_on_frn  -> wf_cnd_trggrer_on_frn
--   wf_proto_trg_at_given_time   -> wf_trg_at_given_time
-- A hotel that imports Habbo's furni gets these rows on `default` (or on an interaction
-- type equal to the classname, which we do not register); both fall through to
-- InteractionDefault, so the box places but opens no editor and never runs.
--
-- Only rows still on one of those two stale types are moved, so a hotel that put the
-- furni on an interaction of its own keeps it. `public_name` is operator-visible and
-- stays as the hotel has it. No table changes.
--
-- Charset handling follows V20260923210000: adopted hotels carry latin1 or either
-- utf8mb4 collation on `items_base`, so the comparison converts the `items_base` side
-- and pins the collation explicitly.

CREATE TEMPORARY TABLE `polaris_items_base_official_wired_names_20261019` (
    `item_identifier` VARCHAR(70) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
    `stale_interaction_type` VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
    `interaction_type` VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
    PRIMARY KEY (`item_identifier`, `stale_interaction_type`)
) ENGINE=MEMORY;

INSERT INTO `polaris_items_base_official_wired_names_20261019`
    (`item_identifier`, `stale_interaction_type`, `interaction_type`)
VALUES
    ('wf_act_progress_ach', 'default', 'wf_act_progress_achievement'),
    ('wf_act_progress_ach', 'wf_act_progress_ach', 'wf_act_progress_achievement'),
    ('wf_act_set_visibility', 'default', 'wf_act_change_opacity'),
    ('wf_act_set_visibility', 'wf_act_set_visibility', 'wf_act_change_opacity'),
    ('wf_trg_room_initializes', 'default', 'wf_trg_room_initialized'),
    ('wf_trg_room_initializes', 'wf_trg_room_initializes', 'wf_trg_room_initialized'),
    ('wf_var_dailytask', 'default', 'wf_var_daily_task'),
    ('wf_var_dailytask', 'wf_var_dailytask', 'wf_var_daily_task'),
    ('wf_ltdproto_act_toggle_state', 'default', 'wf_act_toggle_state'),
    ('wf_ltdproto_act_toggle_state', 'wf_ltdproto_act_toggle_state', 'wf_act_toggle_state'),
    ('wf_proto_cnd_trggrer_on_frn', 'default', 'wf_cnd_trggrer_on_frn'),
    ('wf_proto_cnd_trggrer_on_frn', 'wf_proto_cnd_trggrer_on_frn', 'wf_cnd_trggrer_on_frn'),
    ('wf_proto_trg_at_given_time', 'default', 'wf_trg_at_given_time'),
    ('wf_proto_trg_at_given_time', 'wf_proto_trg_at_given_time', 'wf_trg_at_given_time');

UPDATE `items_base` AS item
INNER JOIN `polaris_items_base_official_wired_names_20261019` AS mapping
    ON mapping.`item_identifier`
        = CONVERT(item.`item_name` USING utf8mb4) COLLATE utf8mb4_general_ci
    AND mapping.`stale_interaction_type`
        = CONVERT(item.`interaction_type` USING utf8mb4) COLLATE utf8mb4_general_ci
SET item.`interaction_type` = mapping.`interaction_type`;

DROP TEMPORARY TABLE `polaris_items_base_official_wired_names_20261019`;
