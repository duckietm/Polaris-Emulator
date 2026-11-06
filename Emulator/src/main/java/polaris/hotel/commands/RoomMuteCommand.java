package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomChatMessageBubbles;

public class RoomMuteCommand extends Command {
    public RoomMuteCommand() {
        super(
                "cmd_roommute",
                Emulator.getTexts().getValue("commands.keys.cmd_roommute").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        Room room = gameClient.getUser().getUserInfo().getCurrentRoom();

        // A value-2 permission passes for anyone with rights here: muting the room is its owner's call.
        if (room != null && !room.isOwner(gameClient.getUser())) {
            gameClient.getUser().whisperLocalized("generic.cannot_do_that", RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (room != null) {
            room.setMuted(!room.isMuted());
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts()
                                    .getValue("commands.succes.cmd_roommute." + (room.isMuted() ? "muted" : "unmuted")),
                            RoomChatMessageBubbles.ALERT);
        }

        return true;
    }
}
