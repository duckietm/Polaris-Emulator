package com.eu.habbo.messages.incoming.navigator;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.modtool.ScripterManager;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomDeleter;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.plugin.events.navigator.NavigatorRoomDeletedEvent;
import java.sql.Connection;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestDeleteRoomEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestDeleteRoomEvent.class);

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        int roomId = this.packet.readInt();

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(roomId);

        if (room != null) {
            if (room.isOwner(this.client.getHabbo())) {
                if (room.getId() == this.client.getHabbo().getHabboInfo().getHomeRoom()) {
                    return;
                }

                if (Emulator.getPluginManager()
                        .fireEvent(new NavigatorRoomDeletedEvent(this.client.getHabbo(), room))
                        .isCancelled()) {
                    return;
                }

                try (Connection connection =
                        Emulator.getDatabase().getDataSource().getConnection()) {
                    RoomDeleter.delete(room, Emulator.getGameEnvironment(), connection, Emulator.getThreading()::run);
                } catch (SQLException e) {
                    LOGGER.error("Caught SQL exception", e);
                }
            } else {
                String message = Emulator.getTexts()
                        .getValue("scripter.warning.room.delete")
                        .replace(
                                "%username%",
                                this.client.getHabbo().getHabboInfo().getUsername())
                        .replace("%roomname%", room.getName())
                        .replace("%roomowner%", room.getOwnerName());
                ScripterManager.scripterDetected(this.client, message);
                LOGGER.info(message);
            }
        }
    }
}
