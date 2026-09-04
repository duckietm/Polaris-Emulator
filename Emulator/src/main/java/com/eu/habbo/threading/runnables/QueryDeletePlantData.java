package com.eu.habbo.threading.runnables;

import com.eu.habbo.Emulator;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QueryDeletePlantData implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(QueryDeletePlantData.class);

    private final int itemId;

    public QueryDeletePlantData(int itemId) {
        this.itemId = itemId;
    }

    @Override
    public void run() {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("DELETE FROM item_plants_data WHERE item_id = ?")) {
            statement.setInt(1, this.itemId);
            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }
}
