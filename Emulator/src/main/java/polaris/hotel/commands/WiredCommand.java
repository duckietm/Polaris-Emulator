package polaris.hotel.commands;

import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.messages.outgoing.users.InClientLinkComposer;

public class WiredCommand extends Command {
    public WiredCommand() {
        super(null, new String[] {"wired"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        Room room = gameClient.getUser().getUserInfo().getCurrentRoom();

        if (room == null) {
            gameClient.getUser().whisper("You need to be inside a room to use :wired.", RoomChatMessageBubbles.ALERT);
            return true;
        }

        if (!room.canInspectWired(gameClient.getUser())) {
            gameClient.sendResponse(new InClientLinkComposer("wired-tools/invalid"));
            return true;
        }

        gameClient.sendResponse(new InClientLinkComposer("wired-tools/show"));
        return true;
    }
}
