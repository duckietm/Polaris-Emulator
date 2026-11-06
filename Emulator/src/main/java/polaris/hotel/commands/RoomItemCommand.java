package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;
import polaris.messages.outgoing.rooms.users.RoomUserHandItemComposer;

public class RoomItemCommand extends Command {
    public RoomItemCommand() {
        super(
                "cmd_roomitem",
                Emulator.getTexts().getValue("commands.keys.cmd_roomitem").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        int itemId = 0;

        if (params.length >= 2) {
            try {
                itemId = Integer.parseInt(params[1]);

                if (itemId < 0) {
                    gameClient
                            .getUser()
                            .whisper(
                                    Emulator.getTexts().getValue("commands.error.cmd_roomitem.positive"),
                                    RoomChatMessageBubbles.ALERT);
                    return true;
                }
            } catch (Exception e) {
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts().getValue("commands.error.cmd_roomitem.no_item"),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }
        }

        for (User user : gameClient.getUser().getUserInfo().getCurrentRoom().getUsers()) {
            user.getRoomUnit().setHandItem(itemId);
            user.getUserInfo()
                    .getCurrentRoom()
                    .sendComposer(new RoomUserHandItemComposer(user.getRoomUnit()).compose());
        }

        if (itemId > 0) {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts()
                                    .getValue("commands.succes.cmd_roomitem.given")
                                    .replace("%item%", itemId + ""),
                            RoomChatMessageBubbles.ALERT);
            return true;
        } else {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts().getValue("commands.succes.cmd_roomitem.removed"),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }
    }
}
