package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessageBubbles;

public class DiagonalCommand extends Command {
    public DiagonalCommand() {
        super(
                "cmd_diagonal",
                Emulator.getTexts().getValue("commands.keys.cmd_diagonal").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (gameClient.getUser().getUserInfo().getCurrentRoom() != null) {
            if (gameClient.getUser().getUserInfo().getCurrentRoom().hasRights(gameClient.getUser())) {
                gameClient
                        .getUser()
                        .getUserInfo()
                        .getCurrentRoom()
                        .moveDiagonally(!gameClient
                                .getUser()
                                .getUserInfo()
                                .getCurrentRoom()
                                .moveDiagonally());

                if (!gameClient.getUser().getUserInfo().getCurrentRoom().moveDiagonally()) {
                    gameClient
                            .getUser()
                            .whisper(
                                    Emulator.getTexts().getValue("commands.succes.cmd_diagonal.disabled"),
                                    RoomChatMessageBubbles.ALERT);
                } else {
                    gameClient
                            .getUser()
                            .whisper(
                                    Emulator.getTexts().getValue("commands.succes.cmd_diagonal.enabled"),
                                    RoomChatMessageBubbles.ALERT);
                }

                return true;
            }
        }

        return false;
    }
}
