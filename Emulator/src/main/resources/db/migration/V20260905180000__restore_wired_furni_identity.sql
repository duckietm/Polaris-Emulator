-- Point the wired furni a catalogue import flattened back at their own class.
--
-- Every published catalogue dump replaces `items_base` wholesale, and the dumps
-- come from hotels that do not carry these wired implementations, so the rows
-- arrive on `default`. ItemManager.getItemInteraction returns InteractionDefault
-- for an unknown type without logging anything, so each of these furni still
-- places, still opens no dialog and still does nothing - while remaining on sale.
--
-- These 51 furni are named exactly after an interaction type ItemManager
-- registers, which is the identity rule the wired subsystem uses everywhere:
-- interaction_type is what decides behaviour, item_name is only a label. A row
-- whose classname is a registered interaction and whose interaction_type is
-- `default` is therefore unambiguous - the hotel is not expressing a choice, it
-- is missing the mapping.
--
-- They are most of the variable, contract, chest and highscore families: 21
-- variable boxes, 8 contract and transaction boxes, 3 chest conditions and the
-- highscore writer, plus 18 others.
--
-- Only rows sitting on `default` are touched, so a hotel that deliberately
-- pointed one of these classnames at another registered class keeps it.
--
-- Charset handling follows V20260822164532: adopted hotels carry latin1 or
-- either utf8mb4 collation on `items_base`, so the comparison converts the
-- `items_base` side and pins the collation explicitly. The identifiers are
-- ASCII furnidata classnames, so the conversion never changes what matches.

CREATE TEMPORARY TABLE `polaris_items_base_wired_identity_20260905` (
    `item_identifier` VARCHAR(70) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
    `interaction_type` VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
    PRIMARY KEY (`item_identifier`)
) ENGINE=MEMORY;

INSERT INTO `polaris_items_base_wired_identity_20260905`
    (`item_identifier`, `interaction_type`)
VALUES
    ('wf_act_cancel_transaction', 'wf_act_cancel_transaction'),
    ('wf_act_change_var_val', 'wf_act_change_var_val'),
    ('wf_act_freeze_habbo', 'wf_act_freeze_habbo'),
    ('wf_act_give_currency', 'wf_act_give_currency'),
    ('wf_act_give_furni', 'wf_act_give_furni'),
    ('wf_act_give_points_highscore', 'wf_act_give_points_highscore'),
    ('wf_act_give_userbadge', 'wf_act_give_userbadge'),
    ('wf_act_give_var', 'wf_act_give_var'),
    ('wf_act_init_transaction', 'wf_act_init_transaction'),
    ('wf_act_remove_var', 'wf_act_remove_var'),
    ('wf_act_unfreeze_habbo', 'wf_act_unfreeze_habbo'),
    ('wf_cnd_chest_has_item_type', 'wf_cnd_chest_has_item_type'),
    ('wf_cnd_chest_has_items', 'wf_cnd_chest_has_items'),
    ('wf_cnd_has_tag', 'wf_cnd_has_tag'),
    ('wf_cnd_has_var', 'wf_cnd_has_var'),
    ('wf_cnd_neg_has_var', 'wf_cnd_neg_has_var'),
    ('wf_cnd_not_habbo_has_credits', 'wf_cnd_not_habbo_has_credits'),
    ('wf_cnd_not_habbo_has_duckets', 'wf_cnd_not_habbo_has_duckets'),
    ('wf_cnd_not_has_tag', 'wf_cnd_not_has_tag'),
    ('wf_cnd_valid_moves', 'wf_cnd_valid_moves'),
    ('wf_cnd_var_age_match', 'wf_cnd_var_age_match'),
    ('wf_cnd_var_val_match', 'wf_cnd_var_val_match'),
    ('wf_contract_payment', 'wf_contract_payment'),
    ('wf_contract_reward', 'wf_contract_reward'),
    ('wf_contract_trade', 'wf_contract_trade'),
    ('wf_slc_furni_with_var', 'wf_slc_furni_with_var'),
    ('wf_slc_remote', 'wf_slc_remote'),
    ('wf_slc_users_with_var', 'wf_slc_users_with_var'),
    ('wf_storage_coins1', 'wf_storage_coins1'),
    ('wf_storage_coins2', 'wf_storage_coins2'),
    ('wf_storage_furni1', 'wf_storage_furni1'),
    ('wf_storage_furni2', 'wf_storage_furni2'),
    ('wf_storage_furni_starter', 'wf_storage_furni_starter'),
    ('wf_trg_transaction_complete', 'wf_trg_transaction_complete'),
    ('wf_trg_transaction_fail', 'wf_trg_transaction_fail'),
    ('wf_trg_var_changed', 'wf_trg_var_changed'),
    ('wf_var_context', 'wf_var_context'),
    ('wf_var_furni', 'wf_var_furni'),
    ('wf_var_quest', 'wf_var_quest'),
    ('wf_var_quest_chain', 'wf_var_quest_chain'),
    ('wf_var_reference', 'wf_var_reference'),
    ('wf_var_room', 'wf_var_room'),
    ('wf_var_user', 'wf_var_user'),
    ('wf_xtra_custom_contract', 'wf_xtra_custom_contract'),
    ('wf_xtra_filter_furni_by_var', 'wf_xtra_filter_furni_by_var'),
    ('wf_xtra_filter_users_by_var', 'wf_xtra_filter_users_by_var'),
    ('wf_xtra_scan_chest_furni_by_type', 'wf_xtra_scan_chest_furni_by_type'),
    ('wf_xtra_text_output_furni_name', 'wf_xtra_text_output_furni_name'),
    ('wf_xtra_text_output_username', 'wf_xtra_text_output_username'),
    ('wf_xtra_text_output_variable', 'wf_xtra_text_output_variable'),
    ('wf_xtra_var_text_connector', 'wf_xtra_var_text_connector');

UPDATE `items_base` AS item
INNER JOIN `polaris_items_base_wired_identity_20260905` AS mapping
    ON mapping.`item_identifier`
        = CONVERT(item.`item_name` USING utf8mb4) COLLATE utf8mb4_general_ci
SET item.`interaction_type` = mapping.`interaction_type`
WHERE CONVERT(item.`interaction_type` USING utf8mb4) COLLATE utf8mb4_general_ci = 'default';

DROP TEMPORARY TABLE `polaris_items_base_wired_identity_20260905`;
