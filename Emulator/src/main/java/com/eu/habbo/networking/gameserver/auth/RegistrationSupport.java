package com.eu.habbo.networking.gameserver.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

final class RegistrationSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegistrationSupport.class);

    private RegistrationSupport() {
    }

    static void materializeCustomLayout(Connection conn, int templateId, int newRoomId) {
        String overrideModel = "0";
        String heightmap = "";
        int doorX = 0, doorY = 0, doorDir = 2;
        try (PreparedStatement sel = conn.prepareStatement(
                "SELECT override_model, heightmap, door_x, door_y, door_dir " +
                        "FROM room_templates WHERE template_id = ? LIMIT 1")) {
            sel.setInt(1, templateId);
            try (ResultSet rs = sel.executeQuery()) {
                if (rs.next()) {
                    overrideModel = rs.getString("override_model");
                    heightmap = rs.getString("heightmap");
                    doorX = rs.getInt("door_x");
                    doorY = rs.getInt("door_y");
                    doorDir = rs.getInt("door_dir");
                }
            }
        } catch (SQLException e) {
            LOGGER.error("[auth/register] reading template layout failed templateId=" + templateId, e);
            return;
        }

        if (!"1".equals(overrideModel) || heightmap == null || heightmap.isEmpty()) {
            return;
        }

        String customName = "custom_" + newRoomId;

        try (PreparedStatement ins = conn.prepareStatement(
                "INSERT INTO room_models_custom (id, name, door_x, door_y, door_dir, heightmap) " +
                        "VALUES (?, ?, ?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE name = VALUES(name), door_x = VALUES(door_x), " +
                        "door_y = VALUES(door_y), door_dir = VALUES(door_dir), heightmap = VALUES(heightmap)")) {
            ins.setInt(1, newRoomId);
            ins.setString(2, customName);
            ins.setInt(3, doorX);
            ins.setInt(4, doorY);
            ins.setInt(5, doorDir);
            ins.setString(6, heightmap);
            ins.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("[auth/register] room_models_custom insert failed roomId=" + newRoomId, e);
            return;
        }

        try (PreparedStatement upd = conn.prepareStatement(
                "UPDATE rooms SET model = ? WHERE id = ? LIMIT 1")) {
            upd.setString(1, customName);
            upd.setInt(2, newRoomId);
            upd.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("[auth/register] rooms.model rename failed roomId=" + newRoomId, e);
        }

        LOGGER.info("[auth/register] materialized custom layout '{}' for roomId={}", customName, newRoomId);
    }

    static void seedUserCurrencies(Connection conn, int userId, int duckets, int diamonds) {
        try (PreparedStatement ins = conn.prepareStatement(
                "INSERT INTO users_currency (user_id, type, amount) VALUES (?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE amount = VALUES(amount)")) {
            if (duckets > 0) {
                ins.setInt(1, userId);
                ins.setInt(2, 0);
                ins.setInt(3, duckets);
                ins.addBatch();
            }
            if (diamonds > 0) {
                ins.setInt(1, userId);
                ins.setInt(2, 5);
                ins.setInt(3, diamonds);
                ins.addBatch();
            }
            ins.executeBatch();
        } catch (SQLException e) {
            LOGGER.error("[auth/register] seeding users_currency failed userId=" + userId
                    + " duckets=" + duckets + " diamonds=" + diamonds, e);
        }
    }

    static void cloneTemplateForUser(Connection conn, int templateId, int userId, String userName) {
        LOGGER.info("[auth/register] cloning template id={} for user id={} name='{}'", templateId, userId, userName);

        try (PreparedStatement check = conn.prepareStatement(
                "SELECT 1 FROM room_templates WHERE template_id = ? AND enabled = '1' LIMIT 1")) {
            check.setInt(1, templateId);
            try (ResultSet rs = check.executeQuery()) {
                if (!rs.next()) {
                    LOGGER.warn("[auth/register] unknown/disabled room template id={} for user id={}", templateId, userId);
                    return;
                }
            }
        } catch (SQLException e) {
            LOGGER.error("[auth/register] template lookup failed for templateId=" + templateId, e);
            return;
        }

        int newRoomId = 0;
        int roomsInserted = 0;
        try (PreparedStatement ins = conn.prepareStatement(
                "INSERT INTO rooms (owner_id, owner_name, name, description, model, password, state, " +
                        "users_max, category, paper_floor, paper_wall, paper_landscape, thickness_wall, " +
                        "thickness_floor, moodlight_data, override_model, trade_mode) " +
                        "(SELECT ?, ?, name, room_description, model, password, state, " +
                        "users_max, category, paper_floor, paper_wall, paper_landscape, thickness_wall, " +
                        "thickness_floor, moodlight_data, override_model, trade_mode " +
                        "FROM room_templates WHERE template_id = ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ins.setInt(1, userId);
            ins.setString(2, userName);
            ins.setInt(3, templateId);
            roomsInserted = ins.executeUpdate();
            try (ResultSet keys = ins.getGeneratedKeys()) {
                if (keys.next()) newRoomId = keys.getInt(1);
            }
        } catch (SQLException e) {
            LOGGER.error("[auth/register] clone rooms failed templateId=" + templateId + " userId=" + userId, e);
            return;
        }

        LOGGER.info("[auth/register] rooms insert: rowsAffected={} newRoomId={}", roomsInserted, newRoomId);

        if (newRoomId <= 0) {
            LOGGER.warn("[auth/register] clone aborted - no roomId returned (templateId={}, userId={})", templateId, userId);
            return;
        }

        materializeCustomLayout(conn, templateId, newRoomId);

        int itemsInserted = 0;
        try (PreparedStatement ins = conn.prepareStatement(
                "INSERT INTO items (user_id, room_id, item_id, wall_pos, x, y, z, rot, " +
                        "extra_data, wired_data, limited_data, guild_id) " +
                        "(SELECT ?, ?, item_id, wall_pos, x, y, z, rot, extra_data, wired_data, '0:0', 0 " +
                        "FROM room_templates_items WHERE template_id = ?)")) {
            ins.setInt(1, userId);
            ins.setInt(2, newRoomId);
            ins.setInt(3, templateId);
            itemsInserted = ins.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("[auth/register] clone items failed templateId=" + templateId
                    + " roomId=" + newRoomId + " userId=" + userId, e);
        }

        LOGGER.info("[auth/register] items insert: rowsAffected={} roomId={}", itemsInserted, newRoomId);

        try (PreparedStatement upd = conn.prepareStatement(
                "UPDATE users SET home_room = ? WHERE id = ? LIMIT 1")) {
            upd.setInt(1, newRoomId);
            upd.setInt(2, userId);
            int rows = upd.executeUpdate();
            LOGGER.info("[auth/register] home_room update: rowsAffected={} userId={} roomId={}", rows, userId, newRoomId);
        } catch (SQLException e) {
            LOGGER.error("[auth/register] setting home_room failed userId=" + userId + " roomId=" + newRoomId, e);
        }
    }
}
