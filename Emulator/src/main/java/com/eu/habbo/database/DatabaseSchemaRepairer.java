package com.eu.habbo.database;

import com.eu.habbo.Emulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseSchemaRepairer {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseSchemaRepairer.class);

    private DatabaseSchemaRepairer() {
    }

    public static void repairKnownSchemaIssues() {
        if (Emulator.getDatabase() == null || Emulator.getDatabase().getDataSource() == null) {
            LOGGER.warn("Database schema repair skipped: database is not available.");
            return;
        }

        long start = System.currentTimeMillis();
        int applied = 0;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            applied += ensureUserInfostandColumns(connection);
            applied += ensureGamePrivacySchema(connection);
            applied += ensureMessengerOfflineSchema(connection);
            applied += ensureMentionsSchema(connection);
            applied += ensureWheelSchema(connection);
            applied += ensureWordfilterColumns(connection);
            applied += ensureRoomBehaviorSettingsSchema(connection);
            applied += ensureHotelViewLandingSchema(connection);
        } catch (SQLException e) {
            LOGGER.error("Database schema auto-repair failed.", e);
            return;
        }

        if (applied > 0) {
            LOGGER.warn("Database schema auto-repair applied {} safe migration(s) in {} ms.", applied, System.currentTimeMillis() - start);
        } else {
            LOGGER.info("Database schema auto-repair -> no changes needed ({} ms).", System.currentTimeMillis() - start);
        }
    }

    private static int ensureUserInfostandColumns(Connection connection) throws SQLException {
        int applied = 0;

        applied += ensureColumn(connection, "users", "background_id",
                "ALTER TABLE `users` ADD COLUMN `background_id` INT(11) NOT NULL DEFAULT 0");
        applied += ensureColumn(connection, "users", "background_border_id",
                "ALTER TABLE `users` ADD COLUMN `background_border_id` INT(11) NOT NULL DEFAULT 0");
        applied += ensureColumn(connection, "users", "background_stand_id",
                "ALTER TABLE `users` ADD COLUMN `background_stand_id` INT(11) NOT NULL DEFAULT 0");
        applied += ensureColumn(connection, "users", "background_overlay_id",
                "ALTER TABLE `users` ADD COLUMN `background_overlay_id` INT(11) NOT NULL DEFAULT 0");
        applied += ensureColumn(connection, "users", "background_card_id",
                "ALTER TABLE `users` ADD COLUMN `background_card_id` INT(11) NOT NULL DEFAULT 0");

        return applied;
    }

    private static int ensureGamePrivacySchema(Connection connection) throws SQLException {
        return ensureColumn(connection, "users_settings", "hide_online",
                "ALTER TABLE `users_settings` ADD COLUMN `hide_online` ENUM('0','1') NOT NULL DEFAULT '0' AFTER `block_friendrequests`");
    }

    private static int ensureRoomBehaviorSettingsSchema(Connection connection) throws SQLException {
        int applied = 0;

        applied += ensureColumn(connection, "rooms", "mute_all_pets",
                "ALTER TABLE `rooms` ADD COLUMN `mute_all_pets` TINYINT(1) NOT NULL DEFAULT 0 AFTER `allow_other_pets_eat`");
        applied += ensureColumn(connection, "rooms", "leave_on_door_tile",
                "ALTER TABLE `rooms` ADD COLUMN `leave_on_door_tile` TINYINT(1) NOT NULL DEFAULT 1 AFTER `allow_walkthrough`");
        applied += ensureColumn(connection, "rooms", "idle_sleep_enabled",
                "ALTER TABLE `rooms` ADD COLUMN `idle_sleep_enabled` TINYINT(1) NOT NULL DEFAULT 0 AFTER `allow_underpass`");
        applied += ensureColumn(connection, "rooms", "idle_sleep_timeout_seconds",
                "ALTER TABLE `rooms` ADD COLUMN `idle_sleep_timeout_seconds` INT NOT NULL DEFAULT 0 AFTER `idle_sleep_enabled`");
        applied += ensureColumn(connection, "rooms", "idle_autokick_enabled",
                "ALTER TABLE `rooms` ADD COLUMN `idle_autokick_enabled` TINYINT(1) NOT NULL DEFAULT 0 AFTER `idle_sleep_timeout_seconds`");
        applied += ensureColumn(connection, "rooms", "idle_autokick_timeout_seconds",
                "ALTER TABLE `rooms` ADD COLUMN `idle_autokick_timeout_seconds` INT NOT NULL DEFAULT 0 AFTER `idle_autokick_enabled`");

        return applied;
    }

    private static int ensureMessengerOfflineSchema(Connection connection) throws SQLException {
        return ensureTable(connection, "messenger_offline", """
                CREATE TABLE IF NOT EXISTS `messenger_offline` (
                    `id` INT(11) NOT NULL AUTO_INCREMENT,
                    `user_id` INT(11) NOT NULL DEFAULT 0,
                    `user_from_id` INT(11) NOT NULL DEFAULT 0,
                    `message` VARCHAR(500) NOT NULL,
                    `sended_on` INT(11) NOT NULL,
                    PRIMARY KEY (`id`),
                    KEY `messenger_offline_recipient` (`user_id`, `id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    private static int ensureMentionsSchema(Connection connection) throws SQLException {
        int applied = 0;

        applied += ensureTable(connection, "habbo_mentions", """
                CREATE TABLE IF NOT EXISTS `habbo_mentions` (
                    `id` INT(11) NOT NULL AUTO_INCREMENT,
                    `target_user_id` INT(11) NOT NULL,
                    `sender_user_id` INT(11) NOT NULL,
                    `sender_username` VARCHAR(64) NOT NULL DEFAULT '',
                    `room_id` INT(11) NOT NULL DEFAULT 0,
                    `room_name` VARCHAR(64) NOT NULL DEFAULT '',
                    `message` VARCHAR(255) NOT NULL DEFAULT '',
                    `mention_type` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '0 = direct (@nick), 1 = broadcast (@all/@friends/@room)',
                    `timestamp` INT(11) NOT NULL DEFAULT 0,
                    `read` TINYINT(1) NOT NULL DEFAULT 0,
                    PRIMARY KEY (`id`),
                    KEY `idx_target_id` (`target_user_id`, `id`),
                    KEY `idx_target_unread` (`target_user_id`, `read`),
                    KEY `idx_target_timestamp` (`target_user_id`, `timestamp`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        applied += ensureColumn(connection, "habbo_mentions", "target_user_id",
                "ALTER TABLE `habbo_mentions` ADD COLUMN `target_user_id` INT(11) NOT NULL DEFAULT 0");
        applied += ensureColumn(connection, "habbo_mentions", "sender_user_id",
                "ALTER TABLE `habbo_mentions` ADD COLUMN `sender_user_id` INT(11) NOT NULL DEFAULT 0");
        applied += ensureColumn(connection, "habbo_mentions", "sender_username",
                "ALTER TABLE `habbo_mentions` ADD COLUMN `sender_username` VARCHAR(64) NOT NULL DEFAULT ''");
        applied += ensureColumn(connection, "habbo_mentions", "room_id",
                "ALTER TABLE `habbo_mentions` ADD COLUMN `room_id` INT(11) NOT NULL DEFAULT 0");
        applied += ensureColumn(connection, "habbo_mentions", "room_name",
                "ALTER TABLE `habbo_mentions` ADD COLUMN `room_name` VARCHAR(64) NOT NULL DEFAULT ''");
        applied += ensureColumn(connection, "habbo_mentions", "message",
                "ALTER TABLE `habbo_mentions` ADD COLUMN `message` VARCHAR(255) NOT NULL DEFAULT ''");
        applied += ensureColumn(connection, "habbo_mentions", "mention_type",
                "ALTER TABLE `habbo_mentions` ADD COLUMN `mention_type` TINYINT(1) NOT NULL DEFAULT 0");
        applied += ensureColumn(connection, "habbo_mentions", "timestamp",
                "ALTER TABLE `habbo_mentions` ADD COLUMN `timestamp` INT(11) NOT NULL DEFAULT 0");
        applied += ensureColumn(connection, "habbo_mentions", "read",
                "ALTER TABLE `habbo_mentions` ADD COLUMN `read` TINYINT(1) NOT NULL DEFAULT 0");

        applied += ensureColumn(connection, "users_settings", "mentions_enabled",
                "ALTER TABLE `users_settings` ADD COLUMN `mentions_enabled` ENUM('0','1') NOT NULL DEFAULT '1' COMMENT 'Receive @nick mention notifications.'");
        applied += ensureColumn(connection, "users_settings", "mass_mentions_enabled",
                "ALTER TABLE `users_settings` ADD COLUMN `mass_mentions_enabled` ENUM('0','1') NOT NULL DEFAULT '1' COMMENT 'Receive broadcast (@all / @friends / @room) mentions.'");

        insertSetting(connection, "mentions.enabled", "1");
        insertSetting(connection, "mentions.max.targets", "50");
        insertSetting(connection, "mentions.cooldown.ms", "3000");
        insertSetting(connection, "mentions.room.cooldown.ms", "15000");
        insertSetting(connection, "mentions.store.limit", "50");
        insertSetting(connection, "mentions.request.cooldown.ms", "2000");
        insertSetting(connection, "mentions.markread.cooldown.ms", "500");
        insertSetting(connection, "mentions.markall.cooldown.ms", "5000");
        insertSetting(connection, "mentions.delete.cooldown.ms", "500");
        insertSetting(connection, "mentions.everyone.aliases", "all,everyone,tutti");
        insertSetting(connection, "mentions.friends.aliases", "friends,amici");
        insertSetting(connection, "mentions.room.aliases", "room,stanza");

        return applied;
    }

    private static int ensureWheelSchema(Connection connection) throws SQLException {
        int applied = 0;

        applied += ensureTable(connection, "wheel_prizes", """
                CREATE TABLE IF NOT EXISTS `wheel_prizes` (
                    `id` INT(11) NOT NULL AUTO_INCREMENT,
                    `type` VARCHAR(16) NOT NULL DEFAULT 'nothing',
                    `value` VARCHAR(64) NOT NULL DEFAULT '',
                    `amount` INT(11) NOT NULL DEFAULT 1,
                    `points_type` INT(11) NOT NULL DEFAULT 5,
                    `weight` INT(11) NOT NULL DEFAULT 1,
                    `label` VARCHAR(64) NOT NULL DEFAULT '',
                    `enabled` TINYINT(1) NOT NULL DEFAULT 1,
                    `sort_order` INT(11) NOT NULL DEFAULT 0,
                    PRIMARY KEY (`id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        applied += ensureColumn(connection, "wheel_prizes", "type",
                "ALTER TABLE `wheel_prizes` ADD COLUMN `type` VARCHAR(16) NOT NULL DEFAULT 'nothing'");
        applied += ensureColumn(connection, "wheel_prizes", "value",
                "ALTER TABLE `wheel_prizes` ADD COLUMN `value` VARCHAR(64) NOT NULL DEFAULT ''");
        applied += ensureColumn(connection, "wheel_prizes", "amount",
                "ALTER TABLE `wheel_prizes` ADD COLUMN `amount` INT(11) NOT NULL DEFAULT 1");
        applied += ensureColumn(connection, "wheel_prizes", "points_type",
                "ALTER TABLE `wheel_prizes` ADD COLUMN `points_type` INT(11) NOT NULL DEFAULT 5");
        applied += ensureColumn(connection, "wheel_prizes", "weight",
                "ALTER TABLE `wheel_prizes` ADD COLUMN `weight` INT(11) NOT NULL DEFAULT 1");
        applied += ensureColumn(connection, "wheel_prizes", "label",
                "ALTER TABLE `wheel_prizes` ADD COLUMN `label` VARCHAR(64) NOT NULL DEFAULT ''");
        applied += ensureColumn(connection, "wheel_prizes", "enabled",
                "ALTER TABLE `wheel_prizes` ADD COLUMN `enabled` TINYINT(1) NOT NULL DEFAULT 1");
        applied += ensureColumn(connection, "wheel_prizes", "sort_order",
                "ALTER TABLE `wheel_prizes` ADD COLUMN `sort_order` INT(11) NOT NULL DEFAULT 0");

        applied += ensureTable(connection, "wheel_user_state", """
                CREATE TABLE IF NOT EXISTS `wheel_user_state` (
                    `user_id` INT(11) NOT NULL,
                    `free_spins` INT(11) NOT NULL DEFAULT 0,
                    `extra_spins` INT(11) NOT NULL DEFAULT 0,
                    `last_reset` INT(11) NOT NULL DEFAULT 0,
                    PRIMARY KEY (`user_id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        applied += ensureColumn(connection, "wheel_user_state", "free_spins",
                "ALTER TABLE `wheel_user_state` ADD COLUMN `free_spins` INT(11) NOT NULL DEFAULT 0");
        applied += ensureColumn(connection, "wheel_user_state", "extra_spins",
                "ALTER TABLE `wheel_user_state` ADD COLUMN `extra_spins` INT(11) NOT NULL DEFAULT 0");
        applied += ensureColumn(connection, "wheel_user_state", "last_reset",
                "ALTER TABLE `wheel_user_state` ADD COLUMN `last_reset` INT(11) NOT NULL DEFAULT 0");

        applied += ensureTable(connection, "wheel_recent_wins", """
                CREATE TABLE IF NOT EXISTS `wheel_recent_wins` (
                    `id` INT(11) NOT NULL AUTO_INCREMENT,
                    `user_id` INT(11) NOT NULL,
                    `username` VARCHAR(64) NOT NULL DEFAULT '',
                    `look` VARCHAR(255) NOT NULL DEFAULT '',
                    `prize_label` VARCHAR(64) NOT NULL DEFAULT '',
                    `won_at` INT(11) NOT NULL DEFAULT 0,
                    PRIMARY KEY (`id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        applied += ensureColumn(connection, "wheel_recent_wins", "user_id",
                "ALTER TABLE `wheel_recent_wins` ADD COLUMN `user_id` INT(11) NOT NULL DEFAULT 0");
        applied += ensureColumn(connection, "wheel_recent_wins", "username",
                "ALTER TABLE `wheel_recent_wins` ADD COLUMN `username` VARCHAR(64) NOT NULL DEFAULT ''");
        applied += ensureColumn(connection, "wheel_recent_wins", "look",
                "ALTER TABLE `wheel_recent_wins` ADD COLUMN `look` VARCHAR(255) NOT NULL DEFAULT ''");
        applied += ensureColumn(connection, "wheel_recent_wins", "prize_label",
                "ALTER TABLE `wheel_recent_wins` ADD COLUMN `prize_label` VARCHAR(64) NOT NULL DEFAULT ''");
        applied += ensureColumn(connection, "wheel_recent_wins", "won_at",
                "ALTER TABLE `wheel_recent_wins` ADD COLUMN `won_at` INT(11) NOT NULL DEFAULT 0");

        insertSetting(connection, "wheel.free_spins_per_day", "1");
        insertSetting(connection, "wheel.spin_cost", "50");
        insertSetting(connection, "wheel.spin_cost_type", "5");

        if (isTableEmpty(connection, "wheel_prizes")) {
            applied += execute(connection, """
                    INSERT INTO `wheel_prizes` (`type`, `amount`, `points_type`, `weight`, `label`, `sort_order`) VALUES
                    ('points', 25, 5, 20, '25 diamonds', 1),
                    ('points', 50, 5, 12, '50 diamonds', 2),
                    ('points', 200, 5, 3, '200 diamonds', 3),
                    ('credits', 100, 0, 15, '100 credits', 4),
                    ('spin', 1, 0, 15, '1 Extra spin', 5),
                    ('spin', 2, 0, 6, '2 Extra spins', 6),
                    ('nothing', 0, 0, 29, 'Oh to bad!', 7)
                    """);
        }

        return applied;
    }

    private static int ensureWordfilterColumns(Connection connection) throws SQLException {
        if (!tableExists(connection, "wordfilter")) {
            return 0;
        }

        return ensureColumn(connection, "wordfilter", "prefix_only",
                "ALTER TABLE `wordfilter` ADD COLUMN `prefix_only` ENUM('0','1') NOT NULL DEFAULT '0' COMMENT 'When 1, this word only applies to custom prefixes, not to chat/motto/guild.'");
    }

    private static int ensureHotelViewLandingSchema(Connection connection) throws SQLException {
        int applied = 0;

        applied += ensureTable(connection, "hotelview_landing_settings", """
                CREATE TABLE IF NOT EXISTS `hotelview_landing_settings` (
                    `id` TINYINT UNSIGNED NOT NULL,
                    `background_url` VARCHAR(512) NOT NULL DEFAULT '',
                    `left_url` VARCHAR(512) NOT NULL DEFAULT '',
                    `right_url` VARCHAR(512) NOT NULL DEFAULT '',
                    `drape_url` VARCHAR(512) NOT NULL DEFAULT '',
                    `left_x` INT NOT NULL DEFAULT -1,
                    `left_y` INT NOT NULL DEFAULT -1,
                    `right_x` INT NOT NULL DEFAULT -1,
                    `right_y` INT NOT NULL DEFAULT -1,
                    `drape_x` INT NOT NULL DEFAULT -1,
                    `drape_y` INT NOT NULL DEFAULT -1,
                    `hall_of_fame_x` INT NOT NULL DEFAULT -1,
                    `hall_of_fame_y` INT NOT NULL DEFAULT -1,
                    PRIMARY KEY (`id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        applied += ensureColumn(connection, "hotelview_landing_settings", "background_url",
                "ALTER TABLE `hotelview_landing_settings` ADD COLUMN `background_url` VARCHAR(512) NOT NULL DEFAULT ''");
        applied += ensureColumn(connection, "hotelview_landing_settings", "left_url",
                "ALTER TABLE `hotelview_landing_settings` ADD COLUMN `left_url` VARCHAR(512) NOT NULL DEFAULT ''");
        applied += ensureColumn(connection, "hotelview_landing_settings", "right_url",
                "ALTER TABLE `hotelview_landing_settings` ADD COLUMN `right_url` VARCHAR(512) NOT NULL DEFAULT ''");
        applied += ensureColumn(connection, "hotelview_landing_settings", "drape_url",
                "ALTER TABLE `hotelview_landing_settings` ADD COLUMN `drape_url` VARCHAR(512) NOT NULL DEFAULT ''");
        applied += ensureColumn(connection, "hotelview_landing_settings", "left_x",
                "ALTER TABLE `hotelview_landing_settings` ADD COLUMN `left_x` INT NOT NULL DEFAULT -1");
        applied += ensureColumn(connection, "hotelview_landing_settings", "left_y",
                "ALTER TABLE `hotelview_landing_settings` ADD COLUMN `left_y` INT NOT NULL DEFAULT -1");
        applied += ensureColumn(connection, "hotelview_landing_settings", "right_x",
                "ALTER TABLE `hotelview_landing_settings` ADD COLUMN `right_x` INT NOT NULL DEFAULT -1");
        applied += ensureColumn(connection, "hotelview_landing_settings", "right_y",
                "ALTER TABLE `hotelview_landing_settings` ADD COLUMN `right_y` INT NOT NULL DEFAULT -1");
        applied += ensureColumn(connection, "hotelview_landing_settings", "drape_x",
                "ALTER TABLE `hotelview_landing_settings` ADD COLUMN `drape_x` INT NOT NULL DEFAULT -1");
        applied += ensureColumn(connection, "hotelview_landing_settings", "drape_y",
                "ALTER TABLE `hotelview_landing_settings` ADD COLUMN `drape_y` INT NOT NULL DEFAULT -1");
        applied += ensureColumn(connection, "hotelview_landing_settings", "hall_of_fame_x",
                "ALTER TABLE `hotelview_landing_settings` ADD COLUMN `hall_of_fame_x` INT NOT NULL DEFAULT -1");
        applied += ensureColumn(connection, "hotelview_landing_settings", "hall_of_fame_y",
                "ALTER TABLE `hotelview_landing_settings` ADD COLUMN `hall_of_fame_y` INT NOT NULL DEFAULT -1");
        applied += ensureColumn(connection, "hotelview_landing_settings", "hall_of_fame_enabled",
                "ALTER TABLE `hotelview_landing_settings` ADD COLUMN `hall_of_fame_enabled` TINYINT(1) NOT NULL DEFAULT 0");
        applied += ensureColumn(connection, "hotelview_landing_settings", "hall_of_fame_mode",
                "ALTER TABLE `hotelview_landing_settings` ADD COLUMN `hall_of_fame_mode` VARCHAR(32) NOT NULL DEFAULT 'latest_registered'");
        applied += ensureColumn(connection, "hotelview_landing_settings", "hall_of_fame_currency_type",
                "ALTER TABLE `hotelview_landing_settings` ADD COLUMN `hall_of_fame_currency_type` INT NOT NULL DEFAULT 0");

        applied += ensureTable(connection, "hotelview_landing_slots", """
                CREATE TABLE IF NOT EXISTS `hotelview_landing_slots` (
                    `id` TINYINT UNSIGNED NOT NULL,
                    `enabled` TINYINT(1) NOT NULL DEFAULT 1,
                    `type` VARCHAR(48) NOT NULL DEFAULT 'promotion',
                    `title` VARCHAR(100) NOT NULL DEFAULT '',
                    `body` VARCHAR(500) NOT NULL DEFAULT '',
                    `image_url` VARCHAR(512) NOT NULL DEFAULT '',
                    `button_text` VARCHAR(100) NOT NULL DEFAULT '',
                    `link` VARCHAR(512) NOT NULL DEFAULT '',
                    `progress` TINYINT UNSIGNED NOT NULL DEFAULT 0,
                    `progress_label` VARCHAR(100) NOT NULL DEFAULT '',
                    `config_json` TEXT NOT NULL,
                    PRIMARY KEY (`id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        applied += ensureColumn(connection, "hotelview_landing_slots", "enabled",
                "ALTER TABLE `hotelview_landing_slots` ADD COLUMN `enabled` TINYINT(1) NOT NULL DEFAULT 1");
        applied += ensureColumn(connection, "hotelview_landing_slots", "type",
                "ALTER TABLE `hotelview_landing_slots` ADD COLUMN `type` VARCHAR(48) NOT NULL DEFAULT 'promotion'");
        applied += ensureVarcharColumn(connection, "hotelview_landing_slots", "type", 48,
                "ALTER TABLE `hotelview_landing_slots` MODIFY COLUMN `type` VARCHAR(48) NOT NULL DEFAULT 'promotion'");
        applied += ensureColumn(connection, "hotelview_landing_slots", "title",
                "ALTER TABLE `hotelview_landing_slots` ADD COLUMN `title` VARCHAR(100) NOT NULL DEFAULT ''");
        applied += ensureColumn(connection, "hotelview_landing_slots", "body",
                "ALTER TABLE `hotelview_landing_slots` ADD COLUMN `body` VARCHAR(500) NOT NULL DEFAULT ''");
        applied += ensureColumn(connection, "hotelview_landing_slots", "image_url",
                "ALTER TABLE `hotelview_landing_slots` ADD COLUMN `image_url` VARCHAR(512) NOT NULL DEFAULT ''");
        applied += ensureColumn(connection, "hotelview_landing_slots", "button_text",
                "ALTER TABLE `hotelview_landing_slots` ADD COLUMN `button_text` VARCHAR(100) NOT NULL DEFAULT ''");
        applied += ensureColumn(connection, "hotelview_landing_slots", "link",
                "ALTER TABLE `hotelview_landing_slots` ADD COLUMN `link` VARCHAR(512) NOT NULL DEFAULT ''");
        applied += ensureColumn(connection, "hotelview_landing_slots", "progress",
                "ALTER TABLE `hotelview_landing_slots` ADD COLUMN `progress` TINYINT UNSIGNED NOT NULL DEFAULT 0");
        applied += ensureColumn(connection, "hotelview_landing_slots", "progress_label",
                "ALTER TABLE `hotelview_landing_slots` ADD COLUMN `progress_label` VARCHAR(100) NOT NULL DEFAULT ''");
        applied += ensureColumn(connection, "hotelview_landing_slots", "config_json",
                "ALTER TABLE `hotelview_landing_slots` ADD COLUMN `config_json` TEXT NOT NULL");
        applied += ensureTable(connection, "hotelview_landing_votes", """
                CREATE TABLE IF NOT EXISTS `hotelview_landing_votes` (
                    `slot_id` TINYINT UNSIGNED NOT NULL,
                    `user_id` INT UNSIGNED NOT NULL,
                    `option_id` TINYINT UNSIGNED NOT NULL,
                    PRIMARY KEY (`slot_id`, `user_id`),
                    KEY `hotelview_landing_votes_option` (`slot_id`, `option_id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);

        applied += insertIgnore(connection, """
                INSERT IGNORE INTO `hotelview_landing_settings` (`id`) VALUES (1)
                """);
        applied += insertIgnore(connection, """
                INSERT IGNORE INTO `hotelview_landing_slots`
                    (`id`, `enabled`, `type`, `title`, `body`, `button_text`, `link`, `progress`, `progress_label`, `config_json`)
                VALUES
                    (1, 1, 'bonus', 'Bonus Bag II every 120 credits!', '', 'Get Credits', 'catalog/open/credits', 100, 'Only 120/120 credits to go!', '{}'),
                    (2, 1, 'promotion', 'Welcome to the Hotel', 'Discover rooms, events and the latest additions to the catalogue.', 'Open Navigator', 'navigator/show', 0, '', '{}'),
                    (3, 1, 'promotion', 'What''s new?', 'Check out the latest furni, offers and activities.', 'Open Catalogue', 'catalog/open', 0, '', '{}'),
                    (4, 1, 'promotion', 'Meet new friends', 'Visit public rooms and discover the Habbo community.', 'Find rooms', 'navigator/show', 0, '', '{}'),
                    (5, 1, 'promotion', 'Need help?', 'Read the Hotel Guide to learn how everything works.', 'Open Help', 'help/show', 0, '', '{}')
                """);
        applied += deleteRows(connection, "DELETE FROM `hotelview_landing_votes` WHERE `slot_id` > 5");
        applied += deleteRows(connection, "DELETE FROM `hotelview_landing_slots` WHERE `id` > 5");

        return applied;
    }

    private static int ensureColumn(Connection connection, String table, String column, String alterSql) throws SQLException {
        if (!tableExists(connection, table) || columnExists(connection, table, column)) {
            return 0;
        }

        return execute(connection, alterSql);
    }

    private static int ensureVarcharColumn(Connection connection, String table, String column, int maximumLength, String alterSql) throws SQLException {
        if (!tableExists(connection, table) || !columnExists(connection, table, column)) {
            return 0;
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT data_type, character_maximum_length FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?")) {
            statement.setString(1, table);
            statement.setString(2, column);

            try (ResultSet set = statement.executeQuery()) {
                if (!set.next() || ("varchar".equalsIgnoreCase(set.getString("data_type")) && set.getInt("character_maximum_length") >= maximumLength)) {
                    return 0;
                }
            }
        }

        return execute(connection, alterSql);
    }

    private static int ensureTable(Connection connection, String table, String createSql) throws SQLException {
        if (tableExists(connection, table)) {
            return 0;
        }

        return execute(connection, createSql);
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ? LIMIT 1")) {
            statement.setString(1, table);
            try (ResultSet set = statement.executeQuery()) {
                return set.next();
            }
        }
    }

    private static boolean columnExists(Connection connection, String table, String column) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ? LIMIT 1")) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet set = statement.executeQuery()) {
                return set.next();
            }
        }
    }

    private static boolean isTableEmpty(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet set = statement.executeQuery("SELECT 1 FROM `" + table + "` LIMIT 1")) {
            return !set.next();
        }
    }

    private static int execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
            LOGGER.info("Database schema auto-repair executed: {}", oneLine(sql));
            return 1;
        }
    }

    private static int insertIgnore(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            int inserted = statement.executeUpdate(sql);

            if (inserted > 0) {
                LOGGER.info("Database schema auto-repair inserted {} default row(s): {}", inserted, oneLine(sql));
                return 1;
            }

            return 0;
        }
    }

    private static int deleteRows(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            int deleted = statement.executeUpdate(sql);

            if (deleted > 0) {
                LOGGER.info("Database schema auto-repair removed {} obsolete row(s): {}", deleted, oneLine(sql));
                return 1;
            }

            return 0;
        }
    }

    private static void insertSetting(Connection connection, String key, String value) {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT IGNORE INTO `emulator_settings` (`key`, `value`) VALUES (?, ?)")) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.execute();
        } catch (SQLException e) {
            LOGGER.warn("Database schema auto-repair skipped optional setting {}: {}", key, e.getMessage());
        }
    }

    private static String oneLine(String sql) {
        String normalized = sql == null ? "" : sql.replaceAll("\\s+", " ").trim();
        return normalized.length() > 140 ? normalized.substring(0, 137) + "..." : normalized;
    }
}
