-- Two chests that were never chests.
--
-- `wf_storage_coins1` and `wf_storage_furni_starter` ship with interaction_type 'default', so the
-- emulator treated them as ordinary furniture: clicking one opened nothing, wired could not reach it,
-- and the storage it advertised did not exist. Both classes have been registered in ItemManager all
-- along -- it was only ever the data that pointed somewhere else.
--
-- Only rows still saying 'default' are touched, so a hotel that already fixed this by hand keeps
-- whatever it chose.

UPDATE `items_base`
SET `interaction_type` = 'wf_storage_coins1'
WHERE `item_name` = 'wf_storage_coins1'
  AND `interaction_type` = 'default';

UPDATE `items_base`
SET `interaction_type` = 'wf_storage_furni_starter'
WHERE `item_name` = 'wf_storage_furni_starter'
  AND `interaction_type` = 'default';
