package com.eu.habbo.habbohotel.rooms;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class RoomBanLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoomBanLoader.class);
    private static final String ACTIVE_BANS_QUERY = "SELECT users.username, users.id, room_bans.* FROM room_bans "
            + "INNER JOIN users ON room_bans.user_id = users.id "
            + "WHERE ends > ? AND room_bans.room_id = ?";

    private RoomBanLoader() {}

    /**
     * Replaces the room's active bans with the stored ones. They are read first and swapped in under
     * the map's lock: clearing up front left the room with no bans while the query ran, and a ban
     * placed meanwhile from another thread raced the refill.
     */
    static void load(Connection connection, Room room, Int2ObjectMap<RoomBan> bannedHabbos) {
        Int2ObjectMap<RoomBan> loaded = new Int2ObjectOpenHashMap<>();

        try (PreparedStatement statement = connection.prepareStatement(ACTIVE_BANS_QUERY)) {
            statement.setInt(1, room.currentUnixTimestamp());
            statement.setInt(2, room.getId());
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    int userId = set.getInt("user_id");
                    if (!loaded.containsKey(userId)) {
                        loaded.put(userId, new RoomBan(set));
                    }
                }
            }
        } catch (SQLException exception) {
            LOGGER.error("Caught SQL exception", exception);
            return;
        }

        synchronized (bannedHabbos) {
            bannedHabbos.clear();
            bannedHabbos.putAll(loaded);
        }
    }
}
