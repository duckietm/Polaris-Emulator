package polaris.hotel.commands;

import java.sql.Connection;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.rooms.RoomCopier;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.outgoing.rooms.ForwardToRoomComposer;

/**
 * {@code :copyroom} copies the room the user stands in, with its settings, furni, wired, floor plan and bots, into
 * a new room of their own and takes them there ({@link RoomCopier}). It creates furni out of nothing, so the
 * permission is the top rank's alone by default.
 */
public class CopyRoomCommand extends Command {
    private static final Logger LOGGER = LoggerFactory.getLogger(CopyRoomCommand.class);

    public CopyRoomCommand() {
        super(
                "cmd_copyroom",
                Emulator.getTexts()
                        .getValue("commands.keys.cmd_copyroom", "copyroom")
                        .split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        User user = gameClient.getUser();
        Room room = user.getUserInfo().getCurrentRoom();

        if (room == null) {
            user.whisper(
                    Emulator.getTexts().getValue("commands.error.cmd_copyroom.no_room"), RoomChatMessageBubbles.ALERT);
            return true;
        }

        // The copy reads the stored room: what is still only in memory is written first.
        room.save();
        for (UserItem item : room.getFloorItems()) item.run();
        for (UserItem item : room.getWallItems()) item.run();

        RoomCopier.Result result = copy(
                room.getId(), user.getUserInfo().getId(), user.getUserInfo().getUsername());

        if (result == null) {
            user.whisper(
                    Emulator.getTexts().getValue("commands.error.cmd_copyroom.failed"), RoomChatMessageBubbles.ALERT);
            return true;
        }

        user.whisper(
                Emulator.getTexts()
                        .getValue("commands.success.cmd_copyroom")
                        .replace("%roomid%", String.valueOf(result.roomId()))
                        .replace("%items%", String.valueOf(result.items()))
                        .replace("%bots%", String.valueOf(result.bots())),
                RoomChatMessageBubbles.ALERT);
        gameClient.sendResponse(new ForwardToRoomComposer(result.roomId()));
        return true;
    }

    /** Writes the copy in one transaction; null when nothing was copied. */
    private static RoomCopier.Result copy(int sourceRoomId, int ownerId, String ownerName) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            RoomCopier.Result result = null;
            try {
                result = RoomCopier.copy(connection, sourceRoomId, ownerId, ownerName);
                if (result == null) {
                    connection.rollback();
                    return null;
                }
                connection.commit();
                return result;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            LOGGER.error("Room {} could not be copied for user {}", sourceRoomId, ownerId, e);
            return null;
        }
    }
}
