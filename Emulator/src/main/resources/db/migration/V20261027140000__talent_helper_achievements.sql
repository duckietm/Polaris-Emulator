-- The helper talent track names these achievements, and the server awards them by name (guide tool, safety quiz,
-- self-moderation settings). Hotels whose achievements table came from an older dump miss some of them, so their
-- tasks were left out of the track and those levels could never be completed. Each achievement that is missing
-- entirely is added with the levels of the base database; an achievement a hotel already has is left as it is.

INSERT INTO `achievements` (`name`, `category`, `level`, `reward_amount`, `reward_type`, `points`, `progress_needed`)
SELECT rows_to_add.* FROM (
    SELECT 'GuideRequester' AS `name`, 'explore' AS `category`, 1 AS `level`, 5 AS `reward_amount`, 0 AS `reward_type`, 0 AS `points`, 2 AS `progress_needed`
    UNION ALL SELECT 'GuideRequester', 'explore', 2, 10, 0, 5, 5
) AS rows_to_add
WHERE NOT EXISTS (SELECT 1 FROM `achievements` WHERE `name` = 'GuideRequester');

INSERT INTO `achievements` (`name`, `category`, `level`, `reward_amount`, `reward_type`, `points`, `progress_needed`)
SELECT rows_to_add.* FROM (
    SELECT 'GuideFeedbackGiver' AS `name`, 'explore' AS `category`, 1 AS `level`, 5 AS `reward_amount`, 0 AS `reward_type`, 5 AS `points`, 2 AS `progress_needed`
    UNION ALL SELECT 'GuideFeedbackGiver', 'explore', 2, 10, 0, 5, 5
) AS rows_to_add
WHERE NOT EXISTS (SELECT 1 FROM `achievements` WHERE `name` = 'GuideFeedbackGiver');

INSERT INTO `achievements` (`name`, `category`, `level`, `reward_amount`, `reward_type`, `points`, `progress_needed`)
SELECT rows_to_add.* FROM (
    SELECT 'GuideAdvertisementReader' AS `name`, 'identity' AS `category`, 1 AS `level`, 5 AS `reward_amount`, 0 AS `reward_type`, 5 AS `points`, 1 AS `progress_needed`
) AS rows_to_add
WHERE NOT EXISTS (SELECT 1 FROM `achievements` WHERE `name` = 'GuideAdvertisementReader');

INSERT INTO `achievements` (`name`, `category`, `level`, `reward_amount`, `reward_type`, `points`, `progress_needed`)
SELECT rows_to_add.* FROM (
    SELECT 'GuideRequestHandler' AS `name`, 'explore' AS `category`, 1 AS `level`, 5 AS `reward_amount`, 0 AS `reward_type`, 5 AS `points`, 2 AS `progress_needed`
    UNION ALL SELECT 'GuideRequestHandler', 'explore', 2, 10, 0, 5, 5
    UNION ALL SELECT 'GuideRequestHandler', 'explore', 3, 15, 0, 10, 10
    UNION ALL SELECT 'GuideRequestHandler', 'explore', 4, 20, 0, 15, 15
    UNION ALL SELECT 'GuideRequestHandler', 'explore', 5, 25, 0, 20, 30
    UNION ALL SELECT 'GuideRequestHandler', 'explore', 6, 30, 0, 25, 55
    UNION ALL SELECT 'GuideRequestHandler', 'explore', 7, 35, 0, 30, 85
    UNION ALL SELECT 'GuideRequestHandler', 'explore', 8, 40, 0, 35, 115
    UNION ALL SELECT 'GuideRequestHandler', 'explore', 9, 45, 0, 40, 150
    UNION ALL SELECT 'GuideRequestHandler', 'explore', 10, 50, 0, 45, 200
) AS rows_to_add
WHERE NOT EXISTS (SELECT 1 FROM `achievements` WHERE `name` = 'GuideRequestHandler');

INSERT INTO `achievements` (`name`, `category`, `level`, `reward_amount`, `reward_type`, `points`, `progress_needed`)
SELECT rows_to_add.* FROM (
    SELECT 'GuideEnrollmentLifetime' AS `name`, 'explore' AS `category`, 1 AS `level`, 5 AS `reward_amount`, 0 AS `reward_type`, 0 AS `points`, 1 AS `progress_needed`
    UNION ALL SELECT 'GuideEnrollmentLifetime', 'explore', 2, 10, 0, 5, 7
    UNION ALL SELECT 'GuideEnrollmentLifetime', 'explore', 3, 15, 0, 10, 14
    UNION ALL SELECT 'GuideEnrollmentLifetime', 'explore', 4, 20, 0, 15, 21
    UNION ALL SELECT 'GuideEnrollmentLifetime', 'explore', 5, 25, 0, 20, 28
    UNION ALL SELECT 'GuideEnrollmentLifetime', 'explore', 6, 30, 0, 25, 56
    UNION ALL SELECT 'GuideEnrollmentLifetime', 'explore', 7, 35, 0, 30, 70
    UNION ALL SELECT 'GuideEnrollmentLifetime', 'explore', 8, 40, 0, 35, 112
    UNION ALL SELECT 'GuideEnrollmentLifetime', 'explore', 9, 45, 0, 40, 168
    UNION ALL SELECT 'GuideEnrollmentLifetime', 'explore', 10, 50, 0, 45, 224
    UNION ALL SELECT 'GuideEnrollmentLifetime', 'explore', 11, 55, 0, 50, 280
    UNION ALL SELECT 'GuideEnrollmentLifetime', 'explore', 12, 60, 0, 55, 365
    UNION ALL SELECT 'GuideEnrollmentLifetime', 'explore', 13, 65, 0, 60, 548
    UNION ALL SELECT 'GuideEnrollmentLifetime', 'explore', 14, 70, 0, 65, 730
    UNION ALL SELECT 'GuideEnrollmentLifetime', 'explore', 15, 75, 0, 70, 913
    UNION ALL SELECT 'GuideEnrollmentLifetime', 'explore', 16, 80, 0, 75, 1095
    UNION ALL SELECT 'GuideEnrollmentLifetime', 'explore', 17, 85, 0, 80, 1278
    UNION ALL SELECT 'GuideEnrollmentLifetime', 'explore', 18, 90, 0, 85, 1460
    UNION ALL SELECT 'GuideEnrollmentLifetime', 'explore', 19, 95, 0, 90, 1643
    UNION ALL SELECT 'GuideEnrollmentLifetime', 'explore', 20, 100, 0, 95, 1825
) AS rows_to_add
WHERE NOT EXISTS (SELECT 1 FROM `achievements` WHERE `name` = 'GuideEnrollmentLifetime');

INSERT INTO `achievements` (`name`, `category`, `level`, `reward_amount`, `reward_type`, `points`, `progress_needed`)
SELECT rows_to_add.* FROM (
    SELECT 'GuideRecommendation' AS `name`, 'explore' AS `category`, 1 AS `level`, 5 AS `reward_amount`, 0 AS `reward_type`, 5 AS `points`, 2 AS `progress_needed`
    UNION ALL SELECT 'GuideRecommendation', 'explore', 2, 10, 0, 5, 5
    UNION ALL SELECT 'GuideRecommendation', 'explore', 3, 15, 0, 10, 10
    UNION ALL SELECT 'GuideRecommendation', 'explore', 4, 20, 0, 15, 15
    UNION ALL SELECT 'GuideRecommendation', 'explore', 5, 25, 0, 20, 30
    UNION ALL SELECT 'GuideRecommendation', 'explore', 6, 30, 0, 25, 55
    UNION ALL SELECT 'GuideRecommendation', 'explore', 7, 35, 0, 30, 85
) AS rows_to_add
WHERE NOT EXISTS (SELECT 1 FROM `achievements` WHERE `name` = 'GuideRecommendation');

INSERT INTO `achievements` (`name`, `category`, `level`, `reward_amount`, `reward_type`, `points`, `progress_needed`)
SELECT rows_to_add.* FROM (
    SELECT 'SafetyQuizGraduate' AS `name`, 'identity' AS `category`, 1 AS `level`, 5 AS `reward_amount`, 0 AS `reward_type`, 5 AS `points`, 1 AS `progress_needed`
) AS rows_to_add
WHERE NOT EXISTS (SELECT 1 FROM `achievements` WHERE `name` = 'SafetyQuizGraduate');

INSERT INTO `achievements` (`name`, `category`, `level`, `reward_amount`, `reward_type`, `points`, `progress_needed`)
SELECT rows_to_add.* FROM (
    SELECT 'SelfModChatFloodFilterSeen' AS `name`, 'tools' AS `category`, 1 AS `level`, 5 AS `reward_amount`, 0 AS `reward_type`, 1 AS `points`, 1 AS `progress_needed`
) AS rows_to_add
WHERE NOT EXISTS (SELECT 1 FROM `achievements` WHERE `name` = 'SelfModChatFloodFilterSeen');

INSERT INTO `achievements` (`name`, `category`, `level`, `reward_amount`, `reward_type`, `points`, `progress_needed`)
SELECT rows_to_add.* FROM (
    SELECT 'SelfModChatHearRangeSeen' AS `name`, 'tools' AS `category`, 1 AS `level`, 5 AS `reward_amount`, 0 AS `reward_type`, 1 AS `points`, 1 AS `progress_needed`
) AS rows_to_add
WHERE NOT EXISTS (SELECT 1 FROM `achievements` WHERE `name` = 'SelfModChatHearRangeSeen');

INSERT INTO `achievements` (`name`, `category`, `level`, `reward_amount`, `reward_type`, `points`, `progress_needed`)
SELECT rows_to_add.* FROM (
    SELECT 'SelfModChatScrollSpeedSeen' AS `name`, 'tools' AS `category`, 1 AS `level`, 5 AS `reward_amount`, 0 AS `reward_type`, 1 AS `points`, 1 AS `progress_needed`
) AS rows_to_add
WHERE NOT EXISTS (SELECT 1 FROM `achievements` WHERE `name` = 'SelfModChatScrollSpeedSeen');

INSERT INTO `achievements` (`name`, `category`, `level`, `reward_amount`, `reward_type`, `points`, `progress_needed`)
SELECT rows_to_add.* FROM (
    SELECT 'SelfModRoomFilterSeen' AS `name`, 'tools' AS `category`, 1 AS `level`, 5 AS `reward_amount`, 0 AS `reward_type`, 5 AS `points`, 1 AS `progress_needed`
) AS rows_to_add
WHERE NOT EXISTS (SELECT 1 FROM `achievements` WHERE `name` = 'SelfModRoomFilterSeen');
