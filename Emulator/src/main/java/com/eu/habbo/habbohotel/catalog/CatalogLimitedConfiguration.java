package com.eu.habbo.habbohotel.catalog;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.OptionalInt;

public class CatalogLimitedConfiguration implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogLimitedConfiguration.class);

    private final int itemId;
    private final LinkedList<Integer> limitedNumbers;
    private int totalSet;
    // Page the item lived on before a committed sale moved it to the sold-out page,
    // so a later compensated legacy purchase can move it back.
    private int soldOutFromPageId = 0;

    public CatalogLimitedConfiguration(int itemId, LinkedList<Integer> availableNumbers, int totalSet) {
        this(
                itemId,
                availableNumbers,
                totalSet,
                Emulator.getConfig().getBoolean("catalog.ltd.random", true)
        );
    }

    CatalogLimitedConfiguration(
            int itemId,
            LinkedList<Integer> availableNumbers,
            int totalSet,
            boolean randomize
    ) {
        this.itemId = itemId;
        this.totalSet = totalSet;
        this.limitedNumbers = availableNumbers;

        if (randomize) {
            Collections.shuffle(this.limitedNumbers);
        } else {
            Collections.reverse(this.limitedNumbers);
        }
    }

    public int getNumber() {
        synchronized (this.limitedNumbers) {
            return this.limitedNumbers.pop();
        }
    }

    OptionalInt pollNumber() {
        synchronized (this.limitedNumbers) {
            return OptionalInt.of(this.limitedNumbers.pop());
        }
    }

    /**
     * Returns a drawn number to the available pool when a purchase that reserved
     * it did not complete, so a failed/compensated limited purchase does not
     * permanently shrink the stock. If drawing the number had emptied the pool
     * and moved the item to the sold-out page, the item is moved back.
     */
    public void restoreNumber(int catalogItemId, int number) {
        synchronized (this.limitedNumbers) {
            if (!this.limitedNumbers.contains(number)) this.limitedNumbers.push(number);

            if (this.soldOutFromPageId > 0) {
                CatalogItem catalogItem = Emulator.getGameEnvironment().getCatalogManager().getCatalogItem(this.itemId);
                if (catalogItem != null) {
                    Emulator.getGameEnvironment().getCatalogManager().moveCatalogItem(catalogItem, this.soldOutFromPageId);
                }
                this.soldOutFromPageId = 0;
            }

            // Clear any reservation limitedSold may have written for this number so
            // the DB row matches the returned-to-pool in-memory state. A no-op when
            // the row is still unsold (user_id = 0).
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement("UPDATE catalog_items_limited SET user_id = 0, timestamp = 0, item_id = 0 WHERE catalog_item_id = ? AND number = ? LIMIT 1")) {
                statement.setInt(1, catalogItemId);
                statement.setInt(2, number);
                statement.execute();
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception restoring limited number", e);
            }
        }
    }

    public void limitedSold(int catalogItemId, Habbo habbo, HabboItem item) {
        synchronized (this.limitedNumbers) {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
                this.limitedSold(connection, catalogItemId, habbo, item);
                this.markSoldOutIfEmpty();
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        }
    }

    public void limitedSold(Connection connection, int catalogItemId, Habbo habbo, HabboItem item) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE catalog_items_limited SET user_id = ?, timestamp = ?, item_id = ? WHERE catalog_item_id = ? AND number = ? AND user_id = 0 LIMIT 1")) {
            statement.setInt(1, habbo.getHabboInfo().getId());
            statement.setInt(2, Emulator.getIntUnixTimestamp());
            statement.setInt(3, item.getId());
            statement.setInt(4, catalogItemId);
            statement.setInt(5, item.getLimitedSells());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Limited catalog number is no longer available: " + item.getLimitedSells());
            }
        }
    }

    public void restoreNumber(int number) {
        synchronized (this.limitedNumbers) {
            if (!this.limitedNumbers.contains(number)) this.limitedNumbers.push(number);
        }
    }

    public void markSoldOutIfEmpty() {
        synchronized (this.limitedNumbers) {
            if (this.limitedNumbers.isEmpty()) {
                CatalogItem catalogItem = Emulator.getGameEnvironment().getCatalogManager().getCatalogItem(this.itemId);
                if (catalogItem != null) {
                    this.soldOutFromPageId = catalogItem.getPageId();
                    Emulator.getGameEnvironment().getCatalogManager().moveCatalogItem(
                            catalogItem, Emulator.getConfig().getInt("catalog.ltd.page.soldout"));
                }
            }
        }
    }

    public void generateNumbers(int starting, int amount) {
        synchronized (this.limitedNumbers) {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("INSERT INTO catalog_items_limited (catalog_item_id, number) VALUES (?, ?)")) {
                statement.setInt(1, this.itemId);

                for (int i = starting; i <= amount; i++) {
                    statement.setInt(2, i);
                    statement.addBatch();
                    this.limitedNumbers.push(i);
                }

                statement.executeBatch();
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }

            this.totalSet += amount;

            if (Emulator.getConfig().getBoolean("catalog.ltd.random", true)) {
                Collections.shuffle(this.limitedNumbers);
            } else {
                Collections.reverse(this.limitedNumbers);
            }
        }
    }

    public int available() {
        return this.limitedNumbers.size();
    }

    public int getTotalSet() {
        return this.totalSet;
    }

    public void setTotalSet(int totalSet) {
        this.totalSet = totalSet;
    }

    @Override
    public void run() {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("UPDATE catalog_items SET limited_stack = ?, limited_sells = ? WHERE id = ?")) {
            statement.setInt(1, this.totalSet);
            statement.setInt(2, this.totalSet - this.available());
            statement.setInt(3, this.itemId);
            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }
}
