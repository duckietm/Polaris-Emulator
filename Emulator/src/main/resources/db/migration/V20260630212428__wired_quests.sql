-- Gen-3 Wired 2.0 quest boxes (derived-variable definition add-ons).
-- Point the inert items_base rows at their new classes. Pattern 013-021.
-- Emulator restart required. Idempotent.
--   wf_var_quest       -> WiredExtraQuest       (extra, dialog code 108) — progress/target/is_complete/percent/remaining
--   wf_var_quest_chain -> WiredExtraQuestChain  (extra, dialog code 109) — current_step/total_steps/is_complete/percent

UPDATE items_base SET interaction_type = 'wf_var_quest'
    WHERE item_name = 'wf_var_quest'       AND interaction_type <> 'wf_var_quest';

UPDATE items_base SET interaction_type = 'wf_var_quest_chain'
    WHERE item_name = 'wf_var_quest_chain' AND interaction_type <> 'wf_var_quest_chain';
