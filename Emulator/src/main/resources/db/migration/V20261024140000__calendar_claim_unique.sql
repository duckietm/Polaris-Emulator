-- One calendar day can be claimed once per user. calendar_rewards_claimed had no key at all, so the
-- "already claimed" check lived only in memory - and the day arrived straight off the packet, where a
-- negative one made every (campaign, day) pair distinct and handed out one reward per request.
--
-- The emulator now rejects a day outside the campaign and claims the row before giving the reward, so
-- this key is what makes that claim atomic across sessions.

-- Collapse duplicates first: the key cannot be added while any exist. The table has no identity column,
-- so each affected group is rebuilt down to its earliest row. A no-op once there are no duplicates left.
CREATE TEMPORARY TABLE `calendar_claim_dedupe` AS
SELECT `user_id`,
       `campaign_id`,
       `day`,
       MIN(`reward_id`) AS `reward_id`,
       MIN(`timestamp`)  AS `timestamp`
FROM `calendar_rewards_claimed`
GROUP BY `user_id`, `campaign_id`, `day`
HAVING COUNT(*) > 1;

DELETE `c`
FROM `calendar_rewards_claimed` `c`
JOIN `calendar_claim_dedupe` `d`
  ON `d`.`user_id` = `c`.`user_id`
 AND `d`.`campaign_id` = `c`.`campaign_id`
 AND `d`.`day` = `c`.`day`;

INSERT INTO `calendar_rewards_claimed` (`user_id`, `campaign_id`, `day`, `reward_id`, `timestamp`)
SELECT `user_id`, `campaign_id`, `day`, `reward_id`, `timestamp` FROM `calendar_claim_dedupe`;

DROP TEMPORARY TABLE `calendar_claim_dedupe`;

ALTER TABLE `calendar_rewards_claimed`
    ADD UNIQUE KEY IF NOT EXISTS `uq_calendar_rewards_claimed_day` (`user_id`, `campaign_id`, `day`);
