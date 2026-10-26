-- One friendship row per direction and one pending request per pair. messenger_friendships and
-- messenger_friendrequests only had plain indexes on these columns, so two accepts or two requests
-- racing each other (two sessions, a bulk accept) could store the same pair twice.

-- Collapse duplicates first, keeping the earliest row: the keys cannot be added while any exist.
-- A no-op once there are no duplicates left.
DELETE `later`
FROM `messenger_friendships` `later`
JOIN `messenger_friendships` `earlier`
  ON `earlier`.`user_one_id` = `later`.`user_one_id`
 AND `earlier`.`user_two_id` = `later`.`user_two_id`
 AND `earlier`.`id` < `later`.`id`;

DELETE `later`
FROM `messenger_friendrequests` `later`
JOIN `messenger_friendrequests` `earlier`
  ON `earlier`.`user_to_id` = `later`.`user_to_id`
 AND `earlier`.`user_from_id` = `later`.`user_from_id`
 AND `earlier`.`id` < `later`.`id`;

ALTER TABLE `messenger_friendships`
    ADD UNIQUE KEY IF NOT EXISTS `uq_messenger_friendships_pair` (`user_one_id`, `user_two_id`);

ALTER TABLE `messenger_friendrequests`
    ADD UNIQUE KEY IF NOT EXISTS `uq_messenger_friendrequests_pair` (`user_to_id`, `user_from_id`);
