package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessageBubbles;

public class BlockAlertCommand extends Command {
    public BlockAlertCommand() {
        super(
                "cmd_blockalert",
                Emulator.getTexts().getValue("commands.keys.cmd_blockalert").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (gameClient.getUser().getUserInfo().getCurrentRoom() != null) {
            gameClient.getUser().getUserStats().blockStaffAlerts =
                    !gameClient.getUser().getUserStats().blockStaffAlerts;
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts()
                                    .getValue("commands.succes.cmd_blockalert")
                                    .replace(
                                            "%state%",
                                            (gameClient.getUser().getUserStats().blockStaffAlerts
                                                    ? Emulator.getTexts().getValue("generic.on")
                                                    : Emulator.getTexts().getValue("generic.off"))),
                            RoomChatMessageBubbles.ALERT);

            return true;
        }

        return false;
    }
}
