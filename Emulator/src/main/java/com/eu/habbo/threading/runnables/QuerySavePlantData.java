package com.eu.habbo.threading.runnables;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.PlantData;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QuerySavePlantData implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(QuerySavePlantData.class);

    private final int itemId;
    private final int countState;
    private final long lastWaterDate;
    private final boolean dead;

    public QuerySavePlantData(int itemId, PlantData data) {
        this.itemId = itemId;
        this.countState = data.getCountState();
        this.lastWaterDate = data.getLastWaterDate();
        this.dead = data.isDead();
    }

    @Override
    public void run() {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement("INSERT INTO item_plants_data"
                        + " (item_id, count_state, last_water_date, state) VALUES (?, ?, ?, ?)"
                        + " ON DUPLICATE KEY UPDATE count_state = VALUES(count_state),"
                        + " last_water_date = VALUES(last_water_date), state = VALUES(state)")) {
            statement.setInt(1, this.itemId);
            statement.setInt(2, this.countState);
            statement.setLong(3, this.lastWaterDate);
            statement.setInt(4, this.dead ? 1 : 0);
            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }
}
