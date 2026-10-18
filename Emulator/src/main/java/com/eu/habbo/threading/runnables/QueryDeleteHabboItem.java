package com.eu.habbo.threading.runnables;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestRelease;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QueryDeleteHabboItem implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(QueryDeleteHabboItem.class);

    private final int itemId;
    private final int ownerId;
    /** Null when only the id is known: the chest store is asked whether it is a chest. */
    private final HabboItem item;

    public QueryDeleteHabboItem(int itemId) {
        this.itemId = itemId;
        this.ownerId = 0;
        this.item = null;
    }

    public QueryDeleteHabboItem(HabboItem item) {
        this.itemId = item.getId();
        this.ownerId = item.getUserId();
        this.item = item;
    }

    @Override
    public void run() {
        // A wired chest gives what it holds back to its owner before its row goes.
        if (this.item != null) ChestRelease.releaseToOwner(this.item);
        else ChestRelease.releaseToOwner(this.itemId, this.ownerId);

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement("DELETE FROM items WHERE id = ?")) {
            statement.setInt(1, this.itemId);
            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }
}
