package com.eu.habbo.threading.runnables;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestRelease;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QueryDeleteHabboItems implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(QueryDeleteHabboItems.class);

    private final List<HabboItem> items;

    public QueryDeleteHabboItems(Collection<HabboItem> items) {
        this.items = new ArrayList<>(items);
    }

    @Override
    public void run() {
        // Wired chests give what they hold back to their owner before their rows go. Placed items
        // are skipped below, so they are skipped here too.
        ChestRelease.releaseAll(
                this.items.stream().filter(item -> item.getRoomId() <= 0).toList());

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement("DELETE FROM items WHERE id = ?")) {
            for (HabboItem item : this.items) {
                if (item.getRoomId() > 0) continue;

                statement.setInt(1, item.getId());
                statement.addBatch();
            }

            statement.executeBatch();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        this.items.clear();
    }
}
