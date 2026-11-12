-- How long an account counts as a "new identity" for the client (chat reminder, welcome bubbles and other
-- new-player hints). Until now every account was reported as new. 0 = no account is new; a negative value keeps
-- the old behaviour (every account new). Idempotent.

INSERT INTO `emulator_settings` (`key`, `value`, `comment`) VALUES
    ('hotel.new_identity.days', '7', 'Days after its creation that an account is a new identity for the client (new-player hints). 0 = never, -1 = every account, as before.')
ON DUPLICATE KEY UPDATE `value` = `value`;
