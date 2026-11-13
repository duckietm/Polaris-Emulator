-- The talent show's respect rule (Habbo Stars): in a room with a stage, respect goes only to a user on the stage and
-- only before an audience. Off by default, so rooms that use stage furni as decoration behave as before.
INSERT IGNORE INTO `emulator_settings` (`key`, `value`) VALUES
    ('hotel.talent.show.enabled', '0'),
    ('hotel.talent.show.min.audience', '5'),
    ('hotel.talent.show.stage.furni', 'hs_circstage;hs_stage_black;hs_stage_blue;hs_stage_pink;hs_stage_long_black;hs_stage_long_blue;hs_stage_long_pink');
