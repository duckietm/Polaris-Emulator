package com.srth.plugin.ActionItems;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.outgoing.rooms.RoomRelativeMapComposer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class ItemActionDatabase {

    public static void updateItemInteractions(Item item, int maxCount) {
        if (item == null)
            return;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE items_base SET interaction_type = 'default', interaction_modes_count = ? WHERE id = ?")) {

            statement.setInt(1, maxCount);
            statement.setInt(2, item.getId());
            statement.execute();

            Emulator.getGameEnvironment().getItemManager().loadItems();
            refreshActiveRooms();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void refreshActiveRooms() {
        synchronized (Emulator.getGameEnvironment().getRoomManager().getActiveRooms()) {
            for (Room room : Emulator.getGameEnvironment().getRoomManager().getActiveRooms()) {
                if (room.isLoaded() && room.getUserCount() > 0) {
                    room.sendComposer(new RoomRelativeMapComposer(room).compose());
                }
            }
        }
    }
}