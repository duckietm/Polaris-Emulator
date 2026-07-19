-- Query-driven index contract. Existing indexes with the same ordered left prefix are reused.
-- No index is removed automatically; redundant candidates are reported by the runtime auditor.

-- users_badges.idx_users_badges_user_badge_slot (user_id,badge_code,slot_id)
SET @polaris_index_columns = 'user_id,badge_code,slot_id';
SET @polaris_index_exists = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users_badges'
        GROUP BY INDEX_NAME
    ) existing_indexes
    WHERE indexed_columns = @polaris_index_columns
       OR indexed_columns LIKE CONCAT(@polaris_index_columns, ',%')
);
SET @polaris_index_sql = IF(
    @polaris_index_exists > 0,
    'DO 0',
    'ALTER TABLE `users_badges` ADD INDEX `idx_users_badges_user_badge_slot` (`user_id`, `badge_code`, `slot_id`)'
);
PREPARE polaris_index_statement FROM @polaris_index_sql;
EXECUTE polaris_index_statement;
DEALLOCATE PREPARE polaris_index_statement;

-- users_badges.idx_users_badges_badge_user (badge_code,user_id)
SET @polaris_index_columns = 'badge_code,user_id';
SET @polaris_index_exists = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users_badges'
        GROUP BY INDEX_NAME
    ) existing_indexes
    WHERE indexed_columns = @polaris_index_columns
       OR indexed_columns LIKE CONCAT(@polaris_index_columns, ',%')
);
SET @polaris_index_sql = IF(
    @polaris_index_exists > 0,
    'DO 0',
    'ALTER TABLE `users_badges` ADD INDEX `idx_users_badges_badge_user` (`badge_code`, `user_id`)'
);
PREPARE polaris_index_statement FROM @polaris_index_sql;
EXECUTE polaris_index_statement;
DEALLOCATE PREPARE polaris_index_statement;

-- room_rights.idx_room_rights_room_user (room_id,user_id)
SET @polaris_index_columns = 'room_id,user_id';
SET @polaris_index_exists = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'room_rights'
        GROUP BY INDEX_NAME
    ) existing_indexes
    WHERE indexed_columns = @polaris_index_columns
       OR indexed_columns LIKE CONCAT(@polaris_index_columns, ',%')
);
SET @polaris_index_sql = IF(
    @polaris_index_exists > 0,
    'DO 0',
    'ALTER TABLE `room_rights` ADD INDEX `idx_room_rights_room_user` (`room_id`, `user_id`)'
);
PREPARE polaris_index_statement FROM @polaris_index_sql;
EXECUTE polaris_index_statement;
DEALLOCATE PREPARE polaris_index_statement;

-- room_rights.idx_room_rights_user_room (user_id,room_id)
SET @polaris_index_columns = 'user_id,room_id';
SET @polaris_index_exists = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'room_rights'
        GROUP BY INDEX_NAME
    ) existing_indexes
    WHERE indexed_columns = @polaris_index_columns
       OR indexed_columns LIKE CONCAT(@polaris_index_columns, ',%')
);
SET @polaris_index_sql = IF(
    @polaris_index_exists > 0,
    'DO 0',
    'ALTER TABLE `room_rights` ADD INDEX `idx_room_rights_user_room` (`user_id`, `room_id`)'
);
PREPARE polaris_index_statement FROM @polaris_index_sql;
EXECUTE polaris_index_statement;
DEALLOCATE PREPARE polaris_index_statement;

-- users_pets.idx_users_pets_user_room (user_id,room_id)
SET @polaris_index_columns = 'user_id,room_id';
SET @polaris_index_exists = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users_pets'
        GROUP BY INDEX_NAME
    ) existing_indexes
    WHERE indexed_columns = @polaris_index_columns
       OR indexed_columns LIKE CONCAT(@polaris_index_columns, ',%')
);
SET @polaris_index_sql = IF(
    @polaris_index_exists > 0,
    'DO 0',
    'ALTER TABLE `users_pets` ADD INDEX `idx_users_pets_user_room` (`user_id`, `room_id`)'
);
PREPARE polaris_index_statement FROM @polaris_index_sql;
EXECUTE polaris_index_statement;
DEALLOCATE PREPARE polaris_index_statement;

-- users_pets.idx_users_pets_room_id (room_id,id)
SET @polaris_index_columns = 'room_id,id';
SET @polaris_index_exists = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users_pets'
        GROUP BY INDEX_NAME
    ) existing_indexes
    WHERE indexed_columns = @polaris_index_columns
       OR indexed_columns LIKE CONCAT(@polaris_index_columns, ',%')
);
SET @polaris_index_sql = IF(
    @polaris_index_exists > 0,
    'DO 0',
    'ALTER TABLE `users_pets` ADD INDEX `idx_users_pets_room_id` (`room_id`, `id`)'
);
PREPARE polaris_index_statement FROM @polaris_index_sql;
EXECUTE polaris_index_statement;
DEALLOCATE PREPARE polaris_index_statement;

-- sanctions.idx_sanctions_habbo_id (habbo_id,id)
SET @polaris_index_columns = 'habbo_id,id';
SET @polaris_index_exists = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sanctions'
        GROUP BY INDEX_NAME
    ) existing_indexes
    WHERE indexed_columns = @polaris_index_columns
       OR indexed_columns LIKE CONCAT(@polaris_index_columns, ',%')
);
SET @polaris_index_sql = IF(
    @polaris_index_exists > 0,
    'DO 0',
    'ALTER TABLE `sanctions` ADD INDEX `idx_sanctions_habbo_id` (`habbo_id`, `id`)'
);
PREPARE polaris_index_statement FROM @polaris_index_sql;
EXECUTE polaris_index_statement;
DEALLOCATE PREPARE polaris_index_statement;

-- sanctions.idx_sanctions_habbo_trade_lock (habbo_id,trade_locked_until)
SET @polaris_index_columns = 'habbo_id,trade_locked_until';
SET @polaris_index_exists = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sanctions'
        GROUP BY INDEX_NAME
    ) existing_indexes
    WHERE indexed_columns = @polaris_index_columns
       OR indexed_columns LIKE CONCAT(@polaris_index_columns, ',%')
);
SET @polaris_index_sql = IF(
    @polaris_index_exists > 0,
    'DO 0',
    'ALTER TABLE `sanctions` ADD INDEX `idx_sanctions_habbo_trade_lock` (`habbo_id`, `trade_locked_until`)'
);
PREPARE polaris_index_statement FROM @polaris_index_sql;
EXECUTE polaris_index_statement;
DEALLOCATE PREPARE polaris_index_statement;

-- messenger_categories.idx_messenger_categories_user_id (user_id,id)
SET @polaris_index_columns = 'user_id,id';
SET @polaris_index_exists = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'messenger_categories'
        GROUP BY INDEX_NAME
    ) existing_indexes
    WHERE indexed_columns = @polaris_index_columns
       OR indexed_columns LIKE CONCAT(@polaris_index_columns, ',%')
);
SET @polaris_index_sql = IF(
    @polaris_index_exists > 0,
    'DO 0',
    'ALTER TABLE `messenger_categories` ADD INDEX `idx_messenger_categories_user_id` (`user_id`, `id`)'
);
PREPARE polaris_index_statement FROM @polaris_index_sql;
EXECUTE polaris_index_statement;
DEALLOCATE PREPARE polaris_index_statement;

-- messenger_friendships.idx_messenger_friendships_user_category (user_one_id,category,user_two_id)
SET @polaris_index_columns = 'user_one_id,category,user_two_id';
SET @polaris_index_exists = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'messenger_friendships'
        GROUP BY INDEX_NAME
    ) existing_indexes
    WHERE indexed_columns = @polaris_index_columns
       OR indexed_columns LIKE CONCAT(@polaris_index_columns, ',%')
);
SET @polaris_index_sql = IF(
    @polaris_index_exists > 0,
    'DO 0',
    'ALTER TABLE `messenger_friendships` ADD INDEX `idx_messenger_friendships_user_category` (`user_one_id`, `category`, `user_two_id`)'
);
PREPARE polaris_index_statement FROM @polaris_index_sql;
EXECUTE polaris_index_statement;
DEALLOCATE PREPARE polaris_index_statement;

-- wired_rewards_given.idx_wired_rewards_item_user_time (wired_item,user_id,timestamp)
SET @polaris_index_columns = 'wired_item,user_id,timestamp';
SET @polaris_index_exists = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wired_rewards_given'
        GROUP BY INDEX_NAME
    ) existing_indexes
    WHERE indexed_columns = @polaris_index_columns
       OR indexed_columns LIKE CONCAT(@polaris_index_columns, ',%')
);
SET @polaris_index_sql = IF(
    @polaris_index_exists > 0,
    'DO 0',
    'ALTER TABLE `wired_rewards_given` ADD INDEX `idx_wired_rewards_item_user_time` (`wired_item`, `user_id`, `timestamp`)'
);
PREPARE polaris_index_statement FROM @polaris_index_sql;
EXECUTE polaris_index_statement;
DEALLOCATE PREPARE polaris_index_statement;

-- users_saved_searches.idx_users_saved_searches_user_id (user_id,id)
SET @polaris_index_columns = 'user_id,id';
SET @polaris_index_exists = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users_saved_searches'
        GROUP BY INDEX_NAME
    ) existing_indexes
    WHERE indexed_columns = @polaris_index_columns
       OR indexed_columns LIKE CONCAT(@polaris_index_columns, ',%')
);
SET @polaris_index_sql = IF(
    @polaris_index_exists > 0,
    'DO 0',
    'ALTER TABLE `users_saved_searches` ADD INDEX `idx_users_saved_searches_user_id` (`user_id`, `id`)'
);
PREPARE polaris_index_statement FROM @polaris_index_sql;
EXECUTE polaris_index_statement;
DEALLOCATE PREPARE polaris_index_statement;

-- users_wardrobe.idx_users_wardrobe_user_slot (user_id,slot_id)
SET @polaris_index_columns = 'user_id,slot_id';
SET @polaris_index_exists = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users_wardrobe'
        GROUP BY INDEX_NAME
    ) existing_indexes
    WHERE indexed_columns = @polaris_index_columns
       OR indexed_columns LIKE CONCAT(@polaris_index_columns, ',%')
);
SET @polaris_index_sql = IF(
    @polaris_index_exists > 0,
    'DO 0',
    'ALTER TABLE `users_wardrobe` ADD INDEX `idx_users_wardrobe_user_slot` (`user_id`, `slot_id`)'
);
PREPARE polaris_index_statement FROM @polaris_index_sql;
EXECUTE polaris_index_statement;
DEALLOCATE PREPARE polaris_index_statement;

-- users_achievements.idx_users_achievements_user_name (user_id,achievement_name)
SET @polaris_index_columns = 'user_id,achievement_name';
SET @polaris_index_exists = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users_achievements'
        GROUP BY INDEX_NAME
    ) existing_indexes
    WHERE indexed_columns = @polaris_index_columns
       OR indexed_columns LIKE CONCAT(@polaris_index_columns, ',%')
);
SET @polaris_index_sql = IF(
    @polaris_index_exists > 0,
    'DO 0',
    'ALTER TABLE `users_achievements` ADD INDEX `idx_users_achievements_user_name` (`user_id`, `achievement_name`)'
);
PREPARE polaris_index_statement FROM @polaris_index_sql;
EXECUTE polaris_index_statement;
DEALLOCATE PREPARE polaris_index_statement;

-- guilds_forums_threads.idx_guild_forum_threads_guild_id (guild_id,id)
SET @polaris_index_columns = 'guild_id,id';
SET @polaris_index_exists = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'guilds_forums_threads'
        GROUP BY INDEX_NAME
    ) existing_indexes
    WHERE indexed_columns = @polaris_index_columns
       OR indexed_columns LIKE CONCAT(@polaris_index_columns, ',%')
);
SET @polaris_index_sql = IF(
    @polaris_index_exists > 0,
    'DO 0',
    'ALTER TABLE `guilds_forums_threads` ADD INDEX `idx_guild_forum_threads_guild_id` (`guild_id`, `id`)'
);
PREPARE polaris_index_statement FROM @polaris_index_sql;
EXECUTE polaris_index_statement;
DEALLOCATE PREPARE polaris_index_statement;

-- marketplace_items.idx_marketplace_items_user_id (user_id,id)
SET @polaris_index_columns = 'user_id,id';
SET @polaris_index_exists = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'marketplace_items'
        GROUP BY INDEX_NAME
    ) existing_indexes
    WHERE indexed_columns = @polaris_index_columns
       OR indexed_columns LIKE CONCAT(@polaris_index_columns, ',%')
);
SET @polaris_index_sql = IF(
    @polaris_index_exists > 0,
    'DO 0',
    'ALTER TABLE `marketplace_items` ADD INDEX `idx_marketplace_items_user_id` (`user_id`, `id`)'
);
PREPARE polaris_index_statement FROM @polaris_index_sql;
EXECUTE polaris_index_statement;
DEALLOCATE PREPARE polaris_index_statement;

-- room_bans.idx_room_bans_room_user_ends (room_id,user_id,ends)
SET @polaris_index_columns = 'room_id,user_id,ends';
SET @polaris_index_exists = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'room_bans'
        GROUP BY INDEX_NAME
    ) existing_indexes
    WHERE indexed_columns = @polaris_index_columns
       OR indexed_columns LIKE CONCAT(@polaris_index_columns, ',%')
);
SET @polaris_index_sql = IF(
    @polaris_index_exists > 0,
    'DO 0',
    'ALTER TABLE `room_bans` ADD INDEX `idx_room_bans_room_user_ends` (`room_id`, `user_id`, `ends`)'
);
PREPARE polaris_index_statement FROM @polaris_index_sql;
EXECUTE polaris_index_statement;
DEALLOCATE PREPARE polaris_index_statement;

-- room_bans.idx_room_bans_ends (ends)
SET @polaris_index_columns = 'ends';
SET @polaris_index_exists = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'room_bans'
        GROUP BY INDEX_NAME
    ) existing_indexes
    WHERE indexed_columns = @polaris_index_columns
       OR indexed_columns LIKE CONCAT(@polaris_index_columns, ',%')
);
SET @polaris_index_sql = IF(
    @polaris_index_exists > 0,
    'DO 0',
    'ALTER TABLE `room_bans` ADD INDEX `idx_room_bans_ends` (`ends`)'
);
PREPARE polaris_index_statement FROM @polaris_index_sql;
EXECUTE polaris_index_statement;
DEALLOCATE PREPARE polaris_index_statement;

-- room_mutes.idx_room_mutes_ends (ends)
SET @polaris_index_columns = 'ends';
SET @polaris_index_exists = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'room_mutes'
        GROUP BY INDEX_NAME
    ) existing_indexes
    WHERE indexed_columns = @polaris_index_columns
       OR indexed_columns LIKE CONCAT(@polaris_index_columns, ',%')
);
SET @polaris_index_sql = IF(
    @polaris_index_exists > 0,
    'DO 0',
    'ALTER TABLE `room_mutes` ADD INDEX `idx_room_mutes_ends` (`ends`)'
);
PREPARE polaris_index_statement FROM @polaris_index_sql;
EXECUTE polaris_index_statement;
DEALLOCATE PREPARE polaris_index_statement;

-- room_votes.idx_room_votes_user_room (user_id,room_id)
SET @polaris_index_columns = 'user_id,room_id';
SET @polaris_index_exists = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'room_votes'
        GROUP BY INDEX_NAME
    ) existing_indexes
    WHERE indexed_columns = @polaris_index_columns
       OR indexed_columns LIKE CONCAT(@polaris_index_columns, ',%')
);
SET @polaris_index_sql = IF(
    @polaris_index_exists > 0,
    'DO 0',
    'ALTER TABLE `room_votes` ADD INDEX `idx_room_votes_user_room` (`user_id`, `room_id`)'
);
PREPARE polaris_index_statement FROM @polaris_index_sql;
EXECUTE polaris_index_statement;
DEALLOCATE PREPARE polaris_index_statement;

-- room_votes.idx_room_votes_room_id (room_id)
SET @polaris_index_columns = 'room_id';
SET @polaris_index_exists = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'room_votes'
        GROUP BY INDEX_NAME
    ) existing_indexes
    WHERE indexed_columns = @polaris_index_columns
       OR indexed_columns LIKE CONCAT(@polaris_index_columns, ',%')
);
SET @polaris_index_sql = IF(
    @polaris_index_exists > 0,
    'DO 0',
    'ALTER TABLE `room_votes` ADD INDEX `idx_room_votes_room_id` (`room_id`)'
);
PREPARE polaris_index_statement FROM @polaris_index_sql;
EXECUTE polaris_index_statement;
DEALLOCATE PREPARE polaris_index_statement;

-- namechange_log.idx_namechange_log_user_time (user_id,timestamp)
SET @polaris_index_columns = 'user_id,timestamp';
SET @polaris_index_exists = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'namechange_log'
        GROUP BY INDEX_NAME
    ) existing_indexes
    WHERE indexed_columns = @polaris_index_columns
       OR indexed_columns LIKE CONCAT(@polaris_index_columns, ',%')
);
SET @polaris_index_sql = IF(
    @polaris_index_exists > 0,
    'DO 0',
    'ALTER TABLE `namechange_log` ADD INDEX `idx_namechange_log_user_time` (`user_id`, `timestamp`)'
);
PREPARE polaris_index_statement FROM @polaris_index_sql;
EXECUTE polaris_index_statement;
DEALLOCATE PREPARE polaris_index_statement;

-- voucher_history.idx_voucher_history_voucher_id (voucher_id)
SET @polaris_index_columns = 'voucher_id';
SET @polaris_index_exists = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'voucher_history'
        GROUP BY INDEX_NAME
    ) existing_indexes
    WHERE indexed_columns = @polaris_index_columns
       OR indexed_columns LIKE CONCAT(@polaris_index_columns, ',%')
);
SET @polaris_index_sql = IF(
    @polaris_index_exists > 0,
    'DO 0',
    'ALTER TABLE `voucher_history` ADD INDEX `idx_voucher_history_voucher_id` (`voucher_id`)'
);
PREPARE polaris_index_statement FROM @polaris_index_sql;
EXECUTE polaris_index_statement;
DEALLOCATE PREPARE polaris_index_statement;

-- calendar_rewards_claimed.idx_calendar_claims_user_campaign_day (user_id,campaign_id,day)
SET @polaris_index_columns = 'user_id,campaign_id,day';
SET @polaris_index_exists = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'calendar_rewards_claimed'
        GROUP BY INDEX_NAME
    ) existing_indexes
    WHERE indexed_columns = @polaris_index_columns
       OR indexed_columns LIKE CONCAT(@polaris_index_columns, ',%')
);
SET @polaris_index_sql = IF(
    @polaris_index_exists > 0,
    'DO 0',
    'ALTER TABLE `calendar_rewards_claimed` ADD INDEX `idx_calendar_claims_user_campaign_day` (`user_id`, `campaign_id`, `day`)'
);
PREPARE polaris_index_statement FROM @polaris_index_sql;
EXECUTE polaris_index_statement;
DEALLOCATE PREPARE polaris_index_statement;

-- user_window_settings.idx_user_window_settings_user_id (user_id)
SET @polaris_index_columns = 'user_id';
SET @polaris_index_exists = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_window_settings'
        GROUP BY INDEX_NAME
    ) existing_indexes
    WHERE indexed_columns = @polaris_index_columns
       OR indexed_columns LIKE CONCAT(@polaris_index_columns, ',%')
);
SET @polaris_index_sql = IF(
    @polaris_index_exists > 0,
    'DO 0',
    'ALTER TABLE `user_window_settings` ADD INDEX `idx_user_window_settings_user_id` (`user_id`)'
);
PREPARE polaris_index_statement FROM @polaris_index_sql;
EXECUTE polaris_index_statement;
DEALLOCATE PREPARE polaris_index_statement;

-- room_trax.idx_room_trax_room_item (room_id,trax_item_id)
SET @polaris_index_columns = 'room_id,trax_item_id';
SET @polaris_index_exists = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'room_trax'
        GROUP BY INDEX_NAME
    ) existing_indexes
    WHERE indexed_columns = @polaris_index_columns
       OR indexed_columns LIKE CONCAT(@polaris_index_columns, ',%')
);
SET @polaris_index_sql = IF(
    @polaris_index_exists > 0,
    'DO 0',
    'ALTER TABLE `room_trax` ADD INDEX `idx_room_trax_room_item` (`room_id`, `trax_item_id`)'
);
PREPARE polaris_index_statement FROM @polaris_index_sql;
EXECUTE polaris_index_statement;
DEALLOCATE PREPARE polaris_index_statement;

-- items_hoppers.idx_items_hoppers_item_id (item_id)
SET @polaris_index_columns = 'item_id';
SET @polaris_index_exists = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'items_hoppers'
        GROUP BY INDEX_NAME
    ) existing_indexes
    WHERE indexed_columns = @polaris_index_columns
       OR indexed_columns LIKE CONCAT(@polaris_index_columns, ',%')
);
SET @polaris_index_sql = IF(
    @polaris_index_exists > 0,
    'DO 0',
    'ALTER TABLE `items_hoppers` ADD INDEX `idx_items_hoppers_item_id` (`item_id`)'
);
PREPARE polaris_index_statement FROM @polaris_index_sql;
EXECUTE polaris_index_statement;
DEALLOCATE PREPARE polaris_index_statement;

-- items_hoppers.idx_items_hoppers_base_item (base_item,item_id)
SET @polaris_index_columns = 'base_item,item_id';
SET @polaris_index_exists = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'items_hoppers'
        GROUP BY INDEX_NAME
    ) existing_indexes
    WHERE indexed_columns = @polaris_index_columns
       OR indexed_columns LIKE CONCAT(@polaris_index_columns, ',%')
);
SET @polaris_index_sql = IF(
    @polaris_index_exists > 0,
    'DO 0',
    'ALTER TABLE `items_hoppers` ADD INDEX `idx_items_hoppers_base_item` (`base_item`, `item_id`)'
);
PREPARE polaris_index_statement FROM @polaris_index_sql;
EXECUTE polaris_index_statement;
DEALLOCATE PREPARE polaris_index_statement;

-- items_presents.idx_items_presents_item_id (item_id)
SET @polaris_index_columns = 'item_id';
SET @polaris_index_exists = (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',') AS indexed_columns
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'items_presents'
        GROUP BY INDEX_NAME
    ) existing_indexes
    WHERE indexed_columns = @polaris_index_columns
       OR indexed_columns LIKE CONCAT(@polaris_index_columns, ',%')
);
SET @polaris_index_sql = IF(
    @polaris_index_exists > 0,
    'DO 0',
    'ALTER TABLE `items_presents` ADD INDEX `idx_items_presents_item_id` (`item_id`)'
);
PREPARE polaris_index_statement FROM @polaris_index_sql;
EXECUTE polaris_index_statement;
DEALLOCATE PREPARE polaris_index_statement;
