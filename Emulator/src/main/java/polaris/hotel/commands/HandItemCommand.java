package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.messages.outgoing.rooms.users.RoomUserHandItemComposer;

public class HandItemCommand extends Command {
    public HandItemCommand() {
        super(
                "cmd_hand_item",
                Emulator.getTexts().getValue("commands.keys.cmd_hand_item").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length == 2) {
            try {
                if (gameClient.getUser().getUserInfo().getCurrentRoom() != null) {
                    int effectId = Integer.parseInt(params[1]);
                    gameClient.getUser().getRoomUnit().setHandItem(effectId);
                    gameClient
                            .getUser()
                            .getUserInfo()
                            .getCurrentRoom()
                            .sendComposer(new RoomUserHandItemComposer(
                                            gameClient.getUser().getRoomUnit())
                                    .compose());
                }
            } catch (Exception e) {
                // Don't handle incorrect parse exceptions :P
            }
        }
        return true;
    }
}
