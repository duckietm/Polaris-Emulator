-- How chat flooding is limited. 'mute' (the default) keeps the classic counter that mutes a flooding
-- speaker; 'budget' limits chat as Habbo does: two lines in hand, one more every 600 ms, and a line
-- over the budget is dropped without a mute (habbicons spend the same budget).
INSERT INTO `emulator_settings` (`key`, `value`, `comment`) VALUES
    ('hotel.chat.flood.mode', 'mute', 'Chat flood limit: mute (classic flood mute) or budget (Habbo''s 2-line budget, +1 line every 600 ms, extra lines dropped, no mute).')
ON DUPLICATE KEY UPDATE `value` = `value`;
