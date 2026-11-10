-- A free claim may let the user choose between several furni (the official client's cursed dragon adoption window sends
-- the claim id and the choice: 0 blue, 1 purple). One row per choice of a claim id; the claim itself stays a row of
-- `special_items_free_claims` (its `enabled` flag switches the whole claim) and is still once per user, whatever was
-- chosen. A claim with no rows here keeps giving its own `item_id`.
--
-- To offer the dragon adoption, add the claim and its two furni, for example:
--   INSERT INTO special_items_free_claims (claim_id, item_id, enabled) VALUES ('cursed_dragon_adoption', <blue furni id>, 1);
--   INSERT INTO special_items_free_claim_choices (claim_id, choice, item_id) VALUES
--     ('cursed_dragon_adoption', 0, <blue furni id>), ('cursed_dragon_adoption', 1, <purple furni id>);
CREATE TABLE IF NOT EXISTS `special_items_free_claim_choices` (
  `claim_id` varchar(64) NOT NULL,
  `choice` int(11) NOT NULL,
  `item_id` int(11) NOT NULL,
  PRIMARY KEY (`claim_id`, `choice`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
