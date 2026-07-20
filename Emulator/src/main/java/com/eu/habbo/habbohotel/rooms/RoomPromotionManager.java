package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.Emulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.IntSupplier;

/**
 * Manages room promotions.
 */
public class RoomPromotionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomPromotionManager.class);

    private final Room room;
    private final IntSupplier roomId;
    private RoomPromotion promotion;
    private volatile boolean promoted;

    public RoomPromotionManager(Room room) {
        this(room, room::getId);
    }

    RoomPromotionManager(Room room, IntSupplier roomId) {
        this.room = room;
        this.roomId = roomId;
        this.promoted = false;
        this.promotion = null;
    }

    /**
     * Loads the promotion from database.
     */
    public void loadPromotion(boolean isPromoted, Connection connection) throws SQLException {
        this.promoted = isPromoted;

        if (!isPromoted) {
            return;
        }

        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT * FROM room_promotions WHERE room_id = ? AND end_timestamp > ? LIMIT 1")) {
            statement.setInt(1, this.roomId.getAsInt());
            statement.setInt(2, Emulator.getIntUnixTimestamp());

            try (ResultSet promotionSet = statement.executeQuery()) {
                RoomPromotion loadedPromotion = promotionSet.next()
                        ? new RoomPromotion(this.room, promotionSet)
                        : null;
                this.promotion = loadedPromotion;
                this.promoted = loadedPromotion != null;
            }
        }
    }

    /**
     * Checks if the room is promoted.
     */
    public boolean isPromoted() {
        this.promoted = this.promotion != null && this.promotion.getEndTimestamp() > Emulator.getIntUnixTimestamp();
        this.room.setNeedsUpdate(true);
        return this.promoted;
    }

    /**
     * Gets the current promotion.
     */
    public RoomPromotion getPromotion() {
        return this.promotion;
    }

    /**
     * Gets the promotion description.
     */
    public String getPromotionDesc() {
        if (this.promotion != null) {
            return this.promotion.getDescription();
        }
        return "";
    }

    /**
     * Creates or updates a room promotion.
     */
    public void createPromotion(String title, String description, int category) {
        this.promoted = true;

        if (this.promotion == null) {
            this.promotion = new RoomPromotion(this.room, title, description,
                Emulator.getIntUnixTimestamp() + (120 * 60), Emulator.getIntUnixTimestamp(), category);
        } else {
            this.promotion.setTitle(title);
            this.promotion.setDescription(description);
            this.promotion.setEndTimestamp(Emulator.getIntUnixTimestamp() + (120 * 60));
            this.promotion.setCategory(category);
        }

        try (Connection connection = Emulator.getDatabase().getDataSource()
            .getConnection(); PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO room_promotions (room_id, title, description, end_timestamp, start_timestamp, category) VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE title = ?, description = ?, end_timestamp = ?, category = ?")) {
            statement.setInt(1, this.room.getId());
            statement.setString(2, title);
            statement.setString(3, description);
            statement.setInt(4, this.promotion.getEndTimestamp());
            statement.setInt(5, this.promotion.getStartTimestamp());
            statement.setInt(6, category);
            statement.setString(7, this.promotion.getTitle());
            statement.setString(8, this.promotion.getDescription());
            statement.setInt(9, this.promotion.getEndTimestamp());
            statement.setInt(10, this.promotion.getCategory());
            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        this.room.setNeedsUpdate(true);
    }

    /**
     * Sets the promoted flag.
     */
    public void setPromoted(boolean promoted) {
        this.promoted = promoted;
    }

    /**
     * Gets the raw promoted flag value.
     */
    public boolean getPromotedFlag() {
        return this.promoted;
    }
}
