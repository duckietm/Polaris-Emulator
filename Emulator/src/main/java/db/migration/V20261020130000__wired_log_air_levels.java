package db.migration;

import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectLog;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Moves the stored level of every "write to logs" box ({@code wf_act_log}, {@code wf_act_neg_log}) to
 * AIR's numbers.
 *
 * <p>Before: 0 debug, 1 info, 2 warning, 3 error. After: 0 info, 1 warn, 2 error, 3 debug, the
 * numbers the official dialog and room log use. A box keeps the level it was set to: old 0 becomes 3,
 * old 1 becomes 0, old 2 becomes 1 and old 3 becomes 2.
 *
 * <p>Boxes are in rooms or inventories ({@code items}), in room templates ({@code room_templates_items})
 * and in wired chests ({@code wired_chest_items}). Only the level's digits change
 * ({@link WiredEffectLog#migrateLegacyLevel}); a box in the old tab format or without a level already
 * reads as info and is left as it is. A row is only rewritten while it still holds what was read.
 */
public final class V20261020130000__wired_log_air_levels extends BaseJavaMigration {
    private static final Logger LOGGER = LoggerFactory.getLogger(V20261020130000__wired_log_air_levels.class);
    private static final String LOG_BOXES = " t JOIN items_base b ON b.id = t.item_id"
            + " WHERE b.interaction_type IN ('wf_act_log', 'wf_act_neg_log') AND t.wired_data LIKE '{%'";
    private static final List<Table> TABLES = List.of(
            new Table(
                    "items",
                    "SELECT t.id, t.wired_data FROM items" + LOG_BOXES,
                    "UPDATE items SET wired_data = ? WHERE id = ? AND wired_data = ?"),
            new Table(
                    "room_templates_items",
                    "SELECT t.id, t.wired_data FROM room_templates_items" + LOG_BOXES,
                    "UPDATE room_templates_items SET wired_data = ? WHERE id = ? AND wired_data = ?"),
            new Table(
                    "wired_chest_items",
                    "SELECT t.id, t.wired_data FROM wired_chest_items" + LOG_BOXES,
                    "UPDATE wired_chest_items SET wired_data = ? WHERE id = ? AND wired_data = ?"));

    private record Table(String name, String select, String update) {}

    private record Row(int id, String stored, String migrated) {}

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        int migrated = 0;

        for (Table table : TABLES) {
            if (tableExists(connection, table.name())) {
                migrated += migrateTable(connection, table);
            }
        }

        LOGGER.info("Moved {} write to logs box(es) to the AIR log levels", migrated);
    }

    private static int migrateTable(Connection connection, Table table) throws SQLException {
        List<Row> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(table.select());
                ResultSet set = statement.executeQuery()) {
            while (set.next()) {
                String stored = set.getString("wired_data");
                String migrated = WiredEffectLog.migrateLegacyLevel(stored);
                if (migrated != null && !migrated.equals(stored)) {
                    rows.add(new Row(set.getInt("id"), stored, migrated));
                }
            }
        }

        int updated = 0;
        if (rows.isEmpty()) {
            return updated;
        }

        try (PreparedStatement statement = connection.prepareStatement(table.update())) {
            for (Row row : rows) {
                statement.setString(1, row.migrated());
                statement.setInt(2, row.id());
                statement.setString(3, row.stored());
                updated += statement.executeUpdate();
            }
        }
        return updated;
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM information_schema.tables"
                + " WHERE table_schema = DATABASE() AND table_name = ? LIMIT 1")) {
            statement.setString(1, table);
            try (ResultSet set = statement.executeQuery()) {
                return set.next();
            }
        }
    }
}
