-- :pay is implemented now. Its texts were seeded with the base database; these are the two it adds: a receiver who is
-- not online and a payment the ledger could not complete. Every statement is idempotent.

INSERT INTO `emulator_texts` (`key`, `value`) VALUES
    ('essentials.cmd_pay.user_offline', '%username% is not online.'),
    ('essentials.cmd_pay.failed', 'The payment to %username% could not be made.')
ON DUPLICATE KEY UPDATE `value` = `value`;
