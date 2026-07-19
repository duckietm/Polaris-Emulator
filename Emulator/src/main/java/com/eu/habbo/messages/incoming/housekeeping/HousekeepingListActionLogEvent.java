package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.housekeeping.HousekeepingActionLogComposer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Read the housekeeping_log audit table. The table isn't part of the
 * Polaris base database yet — operators who want audit have to create
 * it once:
 *
 *   CREATE TABLE IF NOT EXISTS `housekeeping_log` (
 *     `id` INT NOT NULL AUTO_INCREMENT,
 *     `timestamp` INT NOT NULL,
 *     `actor_id` INT NOT NULL,
 *     `actor_name` VARCHAR(64) NOT NULL DEFAULT '',
 *     `target_type` VARCHAR(16) NOT NULL DEFAULT 'user',
 *     `target_id` INT NOT NULL DEFAULT 0,
 *     `target_label` VARCHAR(128) NOT NULL DEFAULT '',
 *     `action` VARCHAR(64) NOT NULL DEFAULT '',
 *     `detail` VARCHAR(500) NOT NULL DEFAULT '',
 *     `success` TINYINT NOT NULL DEFAULT 1,
 *     PRIMARY KEY (`id`), KEY `timestamp` (`timestamp`)
 *   ) ENGINE=InnoDB;
 *
 * If the table is missing we swallow the SQL error and return an empty
 * list — the panel just shows "no audit entries" instead of breaking.
 * Writing into the table is a follow-up: each HK handler will append
 * a row once the table exists; for now the listing is read-only.
 */
public class HousekeepingListActionLogEvent extends MessageHandler {
    private static final int HARD_LIMIT = 500;

    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        if (!this.client.getHabbo().hasPermission(Permission.ACC_HOUSEKEEPING)) {
            return;
        }

        int limit = Math.min(Math.max(this.packet.readInt(), 1), HARD_LIMIT);

        List<HousekeepingActionLogComposer.Row> rows = new ArrayList<>();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, timestamp, actor_id, actor_name, target_type, target_id, target_label, action, detail, success " +
                     "FROM housekeeping_log ORDER BY id DESC LIMIT ?")) {
            statement.setInt(1, limit);

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(new HousekeepingActionLogComposer.Row(
                            rs.getInt("id"),
                            rs.getInt("timestamp"),
                            rs.getInt("actor_id"),
                            rs.getString("actor_name"),
                            rs.getString("target_type"),
                            rs.getInt("target_id"),
                            rs.getString("target_label"),
                            rs.getString("action"),
                            rs.getString("detail"),
                            rs.getInt("success") == 1
                    ));
                }
            }
        } catch (SQLException ignored) {
            // table absent — return empty list, log via emu logger left to the panel UI
        }

        this.client.sendResponse(new HousekeepingActionLogComposer(rows));
    }
}
