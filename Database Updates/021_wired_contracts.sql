-- =====================================================================
-- 021_wired_contracts.sql
-- =====================================================================
-- Phase-2 contracts (gen-3 Origins): config-holder term furni executed by
-- the upgraded Init Transaction. Point the inert items_base rows at their
-- new classes. Pattern 013-020. Emulator restart required. Idempotent.
--
--   wf_contract_payment     -> InteractionWiredContractPayment  (extra, dialog code 110)
--   wf_contract_reward      -> InteractionWiredContractReward   (extra, dialog code 111)
--   wf_contract_trade       -> InteractionWiredContractTrade    (extra, dialog code 112)
--   wf_xtra_custom_contract -> InteractionWiredCustomContract   (extra, dialog code 113)
-- =====================================================================

UPDATE items_base SET interaction_type = 'wf_contract_payment'
    WHERE item_name = 'wf_contract_payment'     AND interaction_type <> 'wf_contract_payment';

UPDATE items_base SET interaction_type = 'wf_contract_reward'
    WHERE item_name = 'wf_contract_reward'      AND interaction_type <> 'wf_contract_reward';

UPDATE items_base SET interaction_type = 'wf_contract_trade'
    WHERE item_name = 'wf_contract_trade'       AND interaction_type <> 'wf_contract_trade';

UPDATE items_base SET interaction_type = 'wf_xtra_custom_contract'
    WHERE item_name = 'wf_xtra_custom_contract' AND interaction_type <> 'wf_xtra_custom_contract';
