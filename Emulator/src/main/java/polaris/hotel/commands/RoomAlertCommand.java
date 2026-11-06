package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.messages.outgoing.modtool.ModToolIssueHandledComposer;

public class RoomAlertCommand extends Command {
    public RoomAlertCommand() {
        super(
                "cmd_roomalert",
                Emulator.getTexts().getValue("commands.keys.cmd_roomalert").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        StringBuilder message = new StringBuilder();

        for (int i = 1; i < params.length; i++) {
            message.append(params[i]).append(" ");
        }

        // An empty alert is answered privately instead of the line being said in the room.
        if (message.toString().trim().isEmpty()) {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts().getValue("commands.error.cmd_roomalert.empty"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }

        Room room = gameClient.getUser().getUserInfo().getCurrentRoom();

        if (room != null) {
            room.sendComposer(new ModToolIssueHandledComposer(message.toString()).compose());
        }

        return true;
    }
}
