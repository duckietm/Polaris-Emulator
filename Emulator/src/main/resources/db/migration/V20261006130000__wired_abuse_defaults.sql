-- Wired limits sized for Habbo-like rooms: a signal per furni of a selection on a fast repeater ran
-- into the old limits and got the room's wired banned for 10 minutes. Over the limit, the rest of the
-- window is now dropped and the room keeps running. Only values still at the old default change, so a
-- hotel that set its own keeps them. Older databases keep some of these keys in emulator_settings.
UPDATE `wired_emulator_settings` SET `value` = '1000' WHERE `key` = 'wired.monitor.delayed.events.limit' AND `value` = '100';
UPDATE `wired_emulator_settings` SET `value` = '10000' WHERE `key` = 'wired.monitor.usage.limit' AND `value` = '1000';
UPDATE `wired_emulator_settings` SET `value` = '1000' WHERE `key` = 'wired.abuse.max.events.per.window' AND `value` = '100';
UPDATE `wired_emulator_settings` SET `value` = '1000' WHERE `key` = 'wired.abuse.rate.limit.window.ms' AND `value` = '10000';
UPDATE `wired_emulator_settings` SET `value` = '0' WHERE `key` = 'wired.abuse.ban.duration.ms' AND `value` = '600000';

UPDATE `wired_emulator_settings` SET `comment` = 'Maximum identical wired events per room inside the rate-limit window; the rest of the window is dropped.'
    WHERE `key` = 'wired.abuse.max.events.per.window';
UPDATE `wired_emulator_settings` SET `comment` = 'Room wired ban in milliseconds once the rate limit is crossed. 0 drops the excess and keeps the room running.'
    WHERE `key` = 'wired.abuse.ban.duration.ms';

UPDATE `emulator_settings` SET `value` = '1000' WHERE `key` = 'wired.monitor.delayed.events.limit' AND `value` = '100';
UPDATE `emulator_settings` SET `value` = '10000' WHERE `key` = 'wired.monitor.usage.limit' AND `value` = '1000';
UPDATE `emulator_settings` SET `value` = '1000' WHERE `key` = 'wired.abuse.max.events.per.window' AND `value` = '100';
UPDATE `emulator_settings` SET `value` = '1000' WHERE `key` = 'wired.abuse.rate.limit.window.ms' AND `value` = '10000';
UPDATE `emulator_settings` SET `value` = '0' WHERE `key` = 'wired.abuse.ban.duration.ms' AND `value` = '600000';
