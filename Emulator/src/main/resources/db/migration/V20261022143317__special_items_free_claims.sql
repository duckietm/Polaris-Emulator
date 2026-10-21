-- Special items display (the `special_items_display/<key>` showcase window): a showcase may offer one free item to
-- everyone who has looked at all of its items. The client only knows the claim id (`special_items.<key>.free_claim`
-- in its configuration) and asks whether it was claimed (395) or claims it (3935); the hotel decides what the claim
-- gives here. One row per claim id, the furni it hands out, and whether it can still be claimed.
CREATE TABLE IF NOT EXISTS `special_items_free_claims` (
  `claim_id` varchar(64) NOT NULL,
  `item_id` int(11) NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`claim_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Who claimed what. The primary key makes a claim once per user, also when two claims race.
CREATE TABLE IF NOT EXISTS `users_special_item_claims` (
  `user_id` int(11) NOT NULL,
  `claim_id` varchar(64) NOT NULL,
  `item_id` int(11) NOT NULL,
  `claimed_at` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`user_id`, `claim_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- The chests showcase (`special_items.chests.*` texts) says the starter furni chest is free to claim from the window.
-- Offer it under the claim id `chests_starter` when the hotel has that furni; insert-if-absent, so an operator's own
-- row for that id is kept.
INSERT INTO `special_items_free_claims` (`claim_id`, `item_id`, `enabled`)
SELECT 'chests_starter', MIN(`id`), 1
FROM `items_base`
WHERE `item_name` = 'wf_storage_furni_starter'
HAVING MIN(`id`) IS NOT NULL
ON DUPLICATE KEY UPDATE `claim_id` = `special_items_free_claims`.`claim_id`;
