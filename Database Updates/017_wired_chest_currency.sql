-- =====================================================================
-- 017_wired_chest_currency.sql
-- =====================================================================
-- Phase-2 chest/storage, currency slice. Point the previously-inert
-- ('default') items_base rows at their newly-registered classes so the
-- emulator loads the real chest/effect/condition. Same pattern as
-- 013/014/015/016. Requires an emulator restart. Idempotent.
--
--   wf_storage_coins1      -> InteractionWiredChestCurrency  (Credit Chest, dialog code 100)
--   wf_storage_coins2      -> InteractionWiredChestCurrency  (Credit Chest)
--   wf_act_give_currency   -> WiredEffectGiveCurrencyFromChest (effect code 99)
--   wf_cnd_chest_has_items -> WiredConditionChestHasItems     (condition code 47)
--
-- NOTE: wf_storage_furni1/2/_starter, give_furni, chest_has_item_type and the
-- scan add-on are the FURNI-chest slice (next) — left 'default' for now.
-- =====================================================================

UPDATE items_base SET interaction_type = 'wf_storage_coins1'
    WHERE item_name = 'wf_storage_coins1'      AND interaction_type <> 'wf_storage_coins1';

UPDATE items_base SET interaction_type = 'wf_storage_coins2'
    WHERE item_name = 'wf_storage_coins2'      AND interaction_type <> 'wf_storage_coins2';

UPDATE items_base SET interaction_type = 'wf_act_give_currency'
    WHERE item_name = 'wf_act_give_currency'   AND interaction_type <> 'wf_act_give_currency';

UPDATE items_base SET interaction_type = 'wf_cnd_chest_has_items'
    WHERE item_name = 'wf_cnd_chest_has_items' AND interaction_type <> 'wf_cnd_chest_has_items';
