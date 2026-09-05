-- Re-apply the wired interaction reconciliation after a catalogue re-import.
--
-- V20260822164532 reconciled 167 furni onto the interaction their wired class is
-- registered under. Flyway records a versioned migration once, so a hotel that
-- later replaces `items_base` wholesale - which every published catalogue dump
-- does, each file carrying its own DROP TABLE - loses those values and never
-- gets them back. Nothing reports it: ItemManager.getItemInteraction falls
-- through to InteractionDefault for an unknown type, so the furni still places,
-- still opens no dialog, and still does nothing.
--
-- These 43 rows are the subset where the reconciliation is not a judgement call:
-- the furni is inert, because it sits on `default` or on a type no class is
-- registered under, and the value restored is one ItemManager does register.
-- Two of them are the antenna: WiredEffectSendSignal and WiredTriggerReceiveSignal
-- recognise it only by an exact `antenna` interaction type, so while wf_antenna1
-- and wf_antenna2 sit on `default` every send-signal chain in the hotel silently
-- does nothing.
--
-- Furni whose interaction was moved onto a *different registered* wired class are
-- deliberately left out: that is a hotel's own naming choice, not damage, and
-- rewriting it would change working behaviour.
--
-- The stale value is carried in the table and matched, so this repairs exactly the
-- states known to be dead and never overwrites an interaction a hotel chose. A row
-- already holding the registered value, or holding some third value of its own, is
-- left untouched.
--
-- Charset handling follows V20260822164532: adopted hotels carry latin1 or either
-- utf8mb4 collation on `items_base`, so every comparison converts the `items_base`
-- side and pins the collation explicitly. The identifiers are ASCII furnidata
-- classnames, so the conversion never changes what matches.

CREATE TEMPORARY TABLE `polaris_items_base_interaction_reapply_20260905` (
    `item_identifier` VARCHAR(70) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
    `stale_interaction_type` VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
    `interaction_type` VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
    PRIMARY KEY (`item_identifier`)
) ENGINE=MEMORY;

INSERT INTO `polaris_items_base_interaction_reapply_20260905`
    (`item_identifier`, `stale_interaction_type`, `interaction_type`)
VALUES
    ('conf_area_hide', 'default', 'wf_conf_area_hide'),
    ('conf_handitem_block', 'conf_handitem_block', 'wf_conf_handitem_block'),
    ('conf_invis_control', 'default', 'wf_conf_invis_control'),
    ('conf_queue_speed', 'default', 'wf_conf_queue_speed'),
    ('conf_wired_disable', 'default', 'wf_conf_wired_disable'),
    ('room_invisible_sit_tile', 'default', 'room_invisible_click_tile'),
    ('wf_act_neg_send_signal', 'default', 'wf_act_neg_send_signal'),
    ('wf_act_send_signal', 'default', 'wf_act_send_signal'),
    ('wf_act_set_altitude', 'default', 'wf_act_set_altitude'),
    ('wf_act_unfreeze', 'wf_act_give_prefix', 'wf_act_unfreeze'),
    ('wf_antenna1', 'default', 'antenna'),
    ('wf_antenna2', 'default', 'antenna'),
    ('wf_cnd_not_triggerer_match', 'default', 'wf_cnd_not_triggerer_match'),
    ('wf_cnd_not_user_performs_action', 'default', 'wf_cnd_not_user_performs_action'),
    ('wf_cnd_team_has_rank', 'default', 'wf_cnd_team_has_rank'),
    ('wf_cnd_team_has_score', 'default', 'wf_cnd_team_has_score'),
    ('wf_cnd_triggerer_match', 'default', 'wf_cnd_triggerer_match'),
    ('wf_cnd_user_performs_action', 'default', 'wf_cnd_user_performs_action'),
    ('wf_pyramid', 'default', 'pyramid'),
    ('wf_slc_furni_altitude', 'default', 'wf_slc_furni_altitude'),
    ('wf_slc_furni_area', 'default', 'wf_slc_furni_area'),
    ('wf_slc_furni_bytype', 'default', 'wf_slc_furni_bytype'),
    ('wf_slc_furni_neighborhood', 'default', 'wf_slc_furni_neighborhood'),
    ('wf_slc_furni_onfurni', 'default', 'wf_slc_furni_onfurni'),
    ('wf_slc_furni_picks', 'default', 'wf_slc_furni_picks'),
    ('wf_slc_furni_signal', 'default', 'wf_slc_furni_signal'),
    ('wf_slc_users_area', 'default', 'wf_slc_users_area'),
    ('wf_slc_users_byaction', 'default', 'wf_slc_users_byaction'),
    ('wf_slc_users_byname', 'default', 'wf_slc_users_byname'),
    ('wf_slc_users_bytype', 'default', 'wf_slc_users_bytype'),
    ('wf_slc_users_group', 'default', 'wf_slc_users_group'),
    ('wf_slc_users_handitem', 'default', 'wf_slc_users_handitem'),
    ('wf_slc_users_neighborhood', 'default', 'wf_slc_users_neighborhood'),
    ('wf_slc_users_onfurni', 'default', 'wf_slc_users_onfurni'),
    ('wf_slc_users_signal', 'default', 'wf_slc_users_signal'),
    ('wf_slc_users_team', 'default', 'wf_slc_users_team'),
    ('wf_trg_recv_signal', 'default', 'wf_trg_recv_signal'),
    ('wf_trg_stuff_state', 'default', 'wf_trg_stuff_state'),
    ('wf_trg_user_performs_action', 'default', 'wf_trg_user_performs_action'),
    ('wf_var_echo', 'default', 'wf_var_echo'),
    ('wf_xtra_filter_furni', 'default', 'wf_xtra_filter_furni'),
    ('wf_xtra_var_lvlup_system', 'default', 'wf_xtra_var_lvlup_system'),
    ('wf_xtra_var_time_util', 'default', 'wf_xtra_var_time_util');

UPDATE `items_base` AS item
INNER JOIN `polaris_items_base_interaction_reapply_20260905` AS mapping
    ON mapping.`item_identifier`
        = CONVERT(item.`item_name` USING utf8mb4) COLLATE utf8mb4_general_ci
SET item.`interaction_type` = mapping.`interaction_type`
WHERE CONVERT(item.`interaction_type` USING utf8mb4) COLLATE utf8mb4_general_ci
    = mapping.`stale_interaction_type`;

DROP TEMPORARY TABLE `polaris_items_base_interaction_reapply_20260905`;
