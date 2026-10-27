-- V20261027120000 gave the helper track's levels achievement ids from the stock database. A hotel whose achievements
-- table is numbered differently got levels without tasks, which counted as completed, so the helper level of its
-- users jumped to the top. The levels now name their achievements (the server reads ids or names), and the helper
-- level the broken rows raised is reset: the track had no levels before, so no one earned a level on it. The server
-- works the level out again when the talent track opens.
-- Only rows still exactly as V20261027120000 wrote them are changed; a hotel's own helper levels are kept.

UPDATE `achievements_talents` SET `achievement_ids` = 'RegistrationDuration'
    WHERE `type` = 'helper' AND `level` = 0 AND `achievement_ids` = '481' AND `achievement_levels` = '1';
UPDATE `achievements_talents` SET `achievement_ids` = 'SelfModChatFloodFilterSeen,SelfModChatHearRangeSeen,SelfModChatScrollSpeedSeen,SelfModRoomFilterSeen'
    WHERE `type` = 'helper' AND `level` = 1 AND `achievement_ids` = '2548,2549,2547,2369' AND `achievement_levels` = '1,1,1,1';
UPDATE `achievements_talents` SET `achievement_ids` = 'GuideRequester,GuideFeedbackGiver'
    WHERE `type` = 'helper' AND `level` = 2 AND `achievement_ids` = '1716,1653' AND `achievement_levels` = '1,1';
UPDATE `achievements_talents` SET `achievement_ids` = 'GuideAdvertisementReader,SafetyQuizGraduate'
    WHERE `type` = 'helper' AND `level` = 3 AND `achievement_ids` = '1903,1705' AND `achievement_levels` = '1,1';
UPDATE `achievements_talents` SET `achievement_ids` = 'RegistrationDuration,GuideFeedbackGiver'
    WHERE `type` = 'helper' AND `level` = 4 AND `achievement_ids` = '481,1653' AND `achievement_levels` = '3,2';
UPDATE `achievements_talents` SET `achievement_ids` = 'GuideRequestHandler,GuideEnrollmentLifetime'
    WHERE `type` = 'helper' AND `level` = 5 AND `achievement_ids` = '1706,1479' AND `achievement_levels` = '1,1';
UPDATE `achievements_talents` SET `achievement_ids` = 'GuideRequestHandler,GuideRecommendation'
    WHERE `type` = 'helper' AND `level` IN (6, 7, 8) AND `achievement_ids` = '1706,1646';

-- Reset the helper level only on hotels where the stock helper rows were added (not hand-made ones).
UPDATE `users_settings` SET `talent_track_helpers_level` = -1
    WHERE `talent_track_helpers_level` <> -1
      AND EXISTS (SELECT 1 FROM `achievements_talents` WHERE `type` = 'helper' AND `level` = 1
                  AND `achievement_ids` = 'SelfModChatFloodFilterSeen,SelfModChatHearRangeSeen,SelfModChatScrollSpeedSeen,SelfModRoomFilterSeen');
