package polaris.threading.runnables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.items.interactions.wired.chest.ChestRelease;
import polaris.hotel.users.UserItem;

public class QueryDeleteUserItems implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(QueryDeleteUserItems.class);

    private final List<UserItem> items;

    public QueryDeleteUserItems(Collection<UserItem> items) {
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
            for (UserItem item : this.items) {
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
