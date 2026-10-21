package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.Emulator;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RoomPromotion {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomPromotion.class);
    private final Room room;
    public boolean needsUpdate;
    private String title;
    private String description;
    private int endTimestamp;
    private int startTimestamp;
    private int category;

    public RoomPromotion(
            Room room, String title, String description, int endTimestamp, int startTimestamp, int category) {
        this.room = room;
        this.title = title;
        this.description = description;
        this.endTimestamp = endTimestamp;
        this.startTimestamp = startTimestamp;
        this.category = category;
    }

    public RoomPromotion(Room room, ResultSet set) throws SQLException {
        this.room = room;
        this.title = set.getString("title");
        this.description = set.getString("description");
        this.endTimestamp = set.getInt("end_timestamp");
        this.startTimestamp = set.getInt("start_timestamp");
        this.category = set.getInt("category");
    }

    public void save() {
        if (this.needsUpdate) {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "UPDATE room_promotions SET title = ?, description = ?, category = ?, end_timestamp = ? WHERE room_id = ?")) {
                statement.setString(1, this.title);
                statement.setString(2, this.description);
                statement.setInt(3, this.category);
                statement.setInt(4, this.endTimestamp);
                statement.setInt(5, this.room.getId());
                statement.executeUpdate();
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }

            this.needsUpdate = false;
        }
    }

    public Room getRoom() {
        return this.room;
    }

    public String getTitle() {
        return this.title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return this.description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getEndTimestamp() {
        return this.endTimestamp;
    }

    public void setEndTimestamp(int endTimestamp) {
        this.endTimestamp = endTimestamp;
    }

    public void addEndTimestamp(int time) {
        this.endTimestamp += time;
    }

    public int getStartTimestamp() {
        return startTimestamp;
    }

    public void setStartTimestamp(int startTimestamp) {
        this.startTimestamp = startTimestamp;
    }

    public int getCategory() {
        return category;
    }

    public void setCategory(int category) {
        this.category = category;
    }
}
