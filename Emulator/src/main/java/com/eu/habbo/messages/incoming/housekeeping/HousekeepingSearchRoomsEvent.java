package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.housekeeping.HousekeepingRoomListComposer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Search rooms by name. `exactMatch=true` => `name = ?` (used by the
 * findByName autocomplete that wants a unique hit). `exactMatch=false`
 * => `name LIKE concat(?, '%')` (used by the prefix dropdown).
 *
 * Both branches go through the same packet because the wire shape is
 * identical — the client picks which mode it wants by toggling the
 * boolean.
 */
public class HousekeepingSearchRoomsEvent extends MessageHandler {
    private static final int HARD_LIMIT = 50;

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        if (!this.client.getHabbo().hasPermission(Permission.ACC_HOUSEKEEPING)) {
            return;
        }

        String query = HousekeepingInputGuard.normalize(this.packet.readString());
        boolean exactMatch = this.packet.readBoolean();
        int limit = Math.min(Math.max(this.packet.readInt(), 1), HARD_LIMIT);

        if (query.isEmpty() || !HousekeepingInputGuard.isWithinLimit(query, HousekeepingInputGuard.MAX_LOOKUP_LENGTH)) {
            this.client.sendResponse(new HousekeepingRoomListComposer(new ArrayList<>()));
            return;
        }

        String sql = exactMatch
                ? "SELECT id FROM rooms WHERE name = ? LIMIT ?"
                : "SELECT id FROM rooms WHERE name LIKE ? LIMIT ?";

        List<Room> rooms = new ArrayList<>();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, exactMatch ? query : com.eu.habbo.util.SqlLikeEscaper.escape(query) + "%");
            statement.setInt(2, limit);

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    Room room = Emulator.getGameEnvironment().getRoomManager().loadRoom(set.getInt("id"), false);

                    if (room != null) rooms.add(room);
                }
            }
        } catch (SQLException ignored) {
            // fall through with whatever we collected before the failure
        }

        this.client.sendResponse(new HousekeepingRoomListComposer(rooms));
    }
}
