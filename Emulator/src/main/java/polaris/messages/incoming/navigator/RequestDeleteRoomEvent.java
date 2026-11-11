package polaris.messages.incoming.navigator;

import java.sql.Connection;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.modtool.ScripterManager;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomDeleter;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.users.UserHomeRoomComposer;
import polaris.plugin.events.navigator.NavigatorRoomDeletedEvent;

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
            if (room.isOwner(this.client.getUser())) {
                if (Emulator.getPluginManager()
                        .fireEvent(new NavigatorRoomDeletedEvent(this.client.getUser(), room))
                        .isCancelled()) {
                    return;
                }

                // Deleting the home room (a starter room is one) leaves the user without a home room, so the
                // next login does not send them to a room that is gone.
                if (room.getId() == this.client.getUser().getUserInfo().getHomeRoom()) {
                    this.client.getUser().getUserInfo().setHomeRoom(0);
                    this.client.sendResponse(new UserHomeRoomComposer(0, 0));
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
                                this.client.getUser().getUserInfo().getUsername())
                        .replace("%roomname%", room.getName())
                        .replace("%roomowner%", room.getOwnerName());
                ScripterManager.scripterDetected(this.client, message);
                LOGGER.info(message);
            }
        }
    }
}
