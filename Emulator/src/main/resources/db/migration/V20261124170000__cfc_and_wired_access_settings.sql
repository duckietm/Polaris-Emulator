-- Two hotel choices the emulator reads with these defaults; the rows are added so a hotel can see and change them.
-- Nothing changes for a hotel until it sets a value. Idempotent: an existing row keeps its value.
--   hotel.furni.cfc.redeemable                1: CFC_ furni are redeemed like CF_ furni (as before).
--                                             0: they stay furni in the exchange window, the redeem command and a
--                                                trade, as on Habbo; wired chest payments still count their value.
--   hotel.wired.access.default.group_admins   0: a room without saved wired settings gives nobody extra wired access
--                                                (as before). 1: its group admins may inspect and modify wired.
INSERT INTO `emulator_settings` (`key`, `value`, `comment`) VALUES
    ('hotel.furni.cfc.redeemable', '1', 'CFC_ credit furni can be redeemed (exchange window, redeem command, trade); 0: they stay furni, as on Habbo.'),
    ('hotel.wired.access.default.group_admins', '0', 'A room without saved wired settings lets its group admins inspect and modify wired; 0: nobody extra.')
ON DUPLICATE KEY UPDATE `value` = `value`;
