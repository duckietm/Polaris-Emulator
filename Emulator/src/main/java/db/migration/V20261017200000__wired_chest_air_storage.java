package db.migration;

import com.eu.habbo.habbohotel.economy.EconomyLedger;
import com.eu.habbo.habbohotel.economy.EconomyOperation;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.LegacyChestMigration;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.LegacyChestMigration.FurniRow;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.LegacyChestMigration.LegacyChest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Moves the wired chests to the official AIR storage model.
 *
 * <p>Before: a chest kept everything (settings, coins, a JSON copy of every furni in it) in its own
 * {@code items.wired_data}, a 10000 character column, and its furni were deleted from {@code items}
 * on deposit and re-created on withdrawal.
 *
 * <p>After: {@code wired_chests} holds a chest's kind, coins, capacity level and settings, and
 * {@code wired_chest_items} holds the furni in it as the items themselves (same id, state and limited
 * number), moved out of {@code items} on deposit and back on withdrawal.
 *
 * <p>Every chest is converted by {@link LegacyChestMigration}, which decides where everything goes so
 * that no player loses furni or currency; each chest is written in its own transaction and a chest
 * that already has a {@code wired_chests} row is skipped, so the migration can be re-run safely.
 */
public final class V20261017200000__wired_chest_air_storage extends BaseJavaMigration {
    private static final Logger LOGGER = LoggerFactory.getLogger(V20261017200000__wired_chest_air_storage.class);

    @Override
    public boolean canExecuteInTransaction() {
        // MariaDB DDL commits implicitly; every chest commits on its own below.
        return false;
    }

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS `wired_chests` (
                        `item_id` INT(11) NOT NULL,
                        `kind` TINYINT(4) NOT NULL DEFAULT 0,
                        `coins` INT(11) NOT NULL DEFAULT 0,
                        `capacity_level` INT(11) NOT NULL DEFAULT 0,
                        `settings` TEXT NOT NULL,
                        PRIMARY KEY (`item_id`)
                    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci""");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS `wired_chest_items` (
                        `id` INT(11) NOT NULL,
                        `chest_id` INT(11) NOT NULL,
                        `sequence` BIGINT(20) NOT NULL DEFAULT 0,
                        `item_id` INT(11) NOT NULL DEFAULT 0,
                        `extra_data` VARCHAR(1024) CHARACTER SET latin1 COLLATE latin1_swedish_ci NOT NULL DEFAULT '',
                        `wired_data` VARCHAR(10000) CHARACTER SET latin1 COLLATE latin1_swedish_ci NOT NULL DEFAULT '',
                        `limited_data` VARCHAR(10) CHARACTER SET latin1 COLLATE latin1_swedish_ci NOT NULL DEFAULT '0:0',
                        `guild_id` INT(11) NOT NULL DEFAULT 0,
                        `expires` INT(11) NOT NULL DEFAULT 0,
                        `allow_underpass` TINYINT(1) NOT NULL DEFAULT 0,
                        PRIMARY KEY (`id`),
                        KEY `idx_wired_chest_items_chest` (`chest_id`, `sequence`)
                    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci""");
        }

        List<LegacyChest> chests = legacyChests(connection);
        long sequence = System.currentTimeMillis() * 1000L;
        int migrated = 0;
        for (LegacyChest chest : chests) {
            LegacyChestMigration.Plan plan = LegacyChestMigration.plan(chest);
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                LegacyChestMigration.apply(plan, new JdbcSink(connection), sequence);
                connection.commit();
                sequence += plan.storedFurni().size() + 1L;
                migrated++;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw new SQLException("Could not migrate wired chest " + chest.itemId(), e);
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
        LOGGER.info("Moved {} wired chest(s) to the AIR chest storage", migrated);
    }

    private static List<LegacyChest> legacyChests(Connection connection) throws SQLException {
        List<String> types = new ArrayList<>(LegacyChestMigration.COIN_CHEST_TYPES);
        types.addAll(LegacyChestMigration.FURNI_CHEST_TYPES);
        String placeholders = String.join(",", types.stream().map(type -> "?").toList());

        List<LegacyChest> chests = new ArrayList<>();
        // An owner that no longer exists reads as 0: nothing is handed to nobody, and such a chest keeps
        // its old data whenever something would have had to go back to its owner.
        try (PreparedStatement statement = connection.prepareStatement("SELECT i.id, COALESCE(u.id, 0) AS user_id,"
                + " i.wired_data, b.interaction_type FROM items i JOIN items_base b ON b.id = i.item_id"
                + " LEFT JOIN users u ON u.id = i.user_id"
                + " LEFT JOIN wired_chests c ON c.item_id = i.id"
                + " WHERE c.item_id IS NULL AND b.interaction_type IN (" + placeholders + ") ORDER BY i.id")) {
            for (int i = 0; i < types.size(); i++) statement.setString(i + 1, types.get(i));
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    chests.add(new LegacyChest(
                            set.getInt("id"),
                            set.getInt("user_id"),
                            set.getString("interaction_type"),
                            set.getString("wired_data")));
                }
            }
        }
        return chests;
    }

    /** Writes a plan with plain JDBC on the migration's connection. */
    private static final class JdbcSink implements LegacyChestMigration.Sink {
        private final Connection connection;
        private int chestId;

        private JdbcSink(Connection connection) {
            this.connection = connection;
        }

        @Override
        public void createChest(int chestId, int kind, int coins, int capacityLevel, String settings) {
            this.chestId = chestId;
            this.update(
                    "INSERT INTO wired_chests (item_id, kind, coins, capacity_level, settings) VALUES (?, ?, ?, ?, ?)",
                    chestId,
                    kind,
                    coins,
                    capacityLevel,
                    settings);
        }

        @Override
        public void storeItem(int chestId, int ownerId, FurniRow furni, long sequence) {
            // The item is created where items are created, so its id comes from the item sequence and
            // can go back into items on withdrawal, then parked in the chest. Columns this insert leaves to
            // the items defaults may be NULL on hotels from Arcturus; the chest table takes its own then.
            int itemId = this.insertItem(ownerId, furni);
            this.update(
                    "INSERT INTO wired_chest_items (id, chest_id, sequence, item_id, extra_data, wired_data, limited_data,"
                            + " guild_id, expires, allow_underpass) SELECT id, ?, ?, item_id, COALESCE(extra_data, ''),"
                            + " COALESCE(wired_data, ''), COALESCE(limited_data, '0:0'), COALESCE(guild_id, 0),"
                            + " COALESCE(expires, 0), COALESCE(allow_underpass, 0) FROM items WHERE id = ?",
                    chestId,
                    sequence,
                    itemId);
            this.update("DELETE FROM items WHERE id = ?", itemId);
        }

        @Override
        public void giveItem(int ownerId, FurniRow furni) {
            this.insertItem(ownerId, furni);
        }

        @Override
        public void giveCredits(int ownerId, int amount) {
            this.refund(ownerId, EconomyLedger.CREDITS, amount);
        }

        @Override
        public void givePoints(int ownerId, int type, int amount) {
            this.refund(ownerId, type, amount);
        }

        /**
         * Through the economy ledger like every wallet change, with an operation id per chest and
         * currency, so a re-run never pays twice.
         */
        private void refund(int ownerId, int type, int amount) {
            try {
                EconomyLedger.apply(
                        this.connection,
                        new EconomyOperation(
                                "wired-chest-air:" + this.chestId + ":" + type,
                                ownerId,
                                null,
                                "wired_chest_migration",
                                "wired.chest.air_storage_refund",
                                type,
                                amount,
                                this.chestId,
                                "Wired chest moved to the AIR storage"));
            } catch (SQLException e) {
                throw new MigrationFailure(e);
            }
        }

        @Override
        public void clearLegacyData(int chestId) {
            this.update("UPDATE items SET wired_data = '' WHERE id = ?", chestId);
        }

        private int insertItem(int ownerId, FurniRow furni) {
            try (PreparedStatement statement = this.connection.prepareStatement(
                    "INSERT INTO items (user_id, room_id, item_id, extra_data, wired_data, limited_data)"
                            + " VALUES (?, 0, ?, ?, '', ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                statement.setInt(1, ownerId);
                statement.setInt(2, furni.baseItemId());
                statement.setString(3, furni.extraData());
                statement.setString(4, furni.limitedData());
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("No id for a migrated chest item");
                    return keys.getInt(1);
                }
            } catch (SQLException e) {
                throw new MigrationFailure(e);
            }
        }

        private void update(String sql, Object... values) {
            try (PreparedStatement statement = this.connection.prepareStatement(sql)) {
                for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new MigrationFailure(e);
            }
        }
    }

    /** Carries a database error out of the sink, so the chest's transaction rolls back. */
    private static final class MigrationFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private MigrationFailure(SQLException cause) {
            super(cause);
        }
    }
}
