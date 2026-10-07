-- :rewardpoints reply when the user's reward track could not be loaded, so
-- nothing was changed. Idempotent.

INSERT INTO `emulator_texts` (`key`, `value`) VALUES
    ('commands.error.cmd_reward_points.load', 'Could not load the reward track of %user%, nothing changed.')
ON DUPLICATE KEY UPDATE `value` = `value`;
