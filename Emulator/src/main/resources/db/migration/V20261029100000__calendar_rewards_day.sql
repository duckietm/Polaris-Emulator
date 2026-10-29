-- Calendar rewards per day. A reward with a day (counted from 1) is given when that day of the campaign is opened;
-- several rewards on the same day are picked at random. Rewards without a day (NULL, as every existing row) stay the
-- random pool for the days that have no reward of their own, so campaigns set up before keep working as they did.
ALTER TABLE `calendar_rewards` ADD COLUMN IF NOT EXISTS `day` INT(11) NULL DEFAULT NULL AFTER `campaign_id`;
