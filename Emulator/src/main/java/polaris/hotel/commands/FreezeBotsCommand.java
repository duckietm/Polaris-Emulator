package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.RoomChatMessageBubbles;

public class FreezeBotsCommand extends Command {
    public FreezeBotsCommand() {
        super(
                "cmd_freeze_bots",
                Emulator.getTexts().getValue("commands.keys.cmd_freeze_bots").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (gameClient.getUser().getUserInfo().getCurrentRoom() != null) {
            if (gameClient.getUser().getUserInfo().getId()
                            == gameClient
                                    .getUser()
                                    .getUserInfo()
                                    .getCurrentRoom()
                                    .getOwnerId()
                    || gameClient.getUser().hasPermission(Permission.ACC_ANYROOMOWNER)) {
                gameClient
                        .getUser()
                        .getUserInfo()
                        .getCurrentRoom()
                        .setAllowBotsWalk(!gameClient
                                .getUser()
                                .getUserInfo()
                                .getCurrentRoom()
                                .isAllowBotsWalk());
                gameClient
                        .getUser()
                        .whisper(
                                gameClient
                                                .getUser()
                                                .getUserInfo()
                                                .getCurrentRoom()
                                                .isAllowBotsWalk()
                                        ? Emulator.getTexts().getValue("commands.succes.cmd_freeze_bots.unfrozen")
                                        : Emulator.getTexts().getValue("commands.succes.cmd_freeze_bots.frozen"),
                                RoomChatMessageBubbles.ALERT);
            } else {
                gameClient
                        .getUser()
                        .whisper(Emulator.getTexts().getValue("generic.cannot_do_that"), RoomChatMessageBubbles.ALERT);
            }

            return true;
        }

        return false;
    }
}
