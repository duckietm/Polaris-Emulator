-- Furni-Matic settings the emulator already reads with these defaults; the rows are added so a hotel can see and
-- change them. Nothing changes for a hotel until it sets a value. Idempotent: an existing row keeps its value.
--   recycler.ducket_cost       Duckets one exchange costs; 0 = free.
--   recycler.timeout_seconds   seconds a user waits between two exchanges; 0 = no wait.
--   hotel.ecotron.box          the box an exchange gives: ecotron_box (classic) or matic_box (the "Mystery Box").
--                              Boxes of either kind keep opening. Read at start-up.

INSERT INTO `emulator_settings` (`key`, `value`, `comment`) VALUES
    ('recycler.ducket_cost', '0', 'Duckets one Furni-Matic exchange costs; 0: free.'),
    ('recycler.timeout_seconds', '0', 'Seconds a user waits between two Furni-Matic exchanges; 0: no wait.'),
    ('hotel.ecotron.box', 'ecotron_box', 'Box a Furni-Matic exchange gives: ecotron_box (classic) or matic_box (Mystery Box). Read at start-up.')
ON DUPLICATE KEY UPDATE `value` = `value`;

-- The Mystery Box is a reward, not a trade good: it cannot be traded, sold on the marketplace, gifted or recycled.
-- The values are written as '0': these columns are ENUM('0','1') in older hotels and TINYINT in newer ones, and a
-- bare 0 is no valid ENUM value ("Data truncated for column 'allow_trade'").
-- Only where nobody owns one yet, so a hotel that already hands out matic_box as an ordinary furni keeps its rules.
UPDATE `items_base` base
SET base.`allow_trade` = '0', base.`allow_marketplace_sell` = '0', base.`allow_gift` = '0', base.`allow_recycle` = '0'
WHERE base.`item_name` = 'matic_box'
  AND NOT EXISTS (SELECT 1 FROM `items` owned WHERE owned.`item_id` = base.`id`);
