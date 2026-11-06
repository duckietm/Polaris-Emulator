package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.messages.outgoing.rooms.users.RoomUserDataComposer;
import polaris.messages.outgoing.users.UpdateUserLookComposer;

public class FacelessCommand extends Command {
    public FacelessCommand() {
        super(
                "cmd_faceless",
                Emulator.getTexts().getValue("commands.keys.cmd_faceless").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (gameClient.getUser().getUserInfo().getCurrentRoom() != null) {
            try {

                String[] figureParts =
                        gameClient.getUser().getUserInfo().getLook().split("\\.");

                for (String part : figureParts) {
                    if (part.startsWith("hd")) {
                        String[] headParts = part.split("-");

                        if (!headParts[1].equals("99999")) headParts[1] = "99999";
                        else break;

                        String newHead = "hd-" + headParts[1] + "-" + headParts[2];

                        gameClient
                                .getUser()
                                .getUserInfo()
                                .setLook(gameClient
                                        .getUser()
                                        .getUserInfo()
                                        .getLook()
                                        .replace(part, newHead));
                        gameClient.sendResponse(new UpdateUserLookComposer(gameClient.getUser()));
                        gameClient
                                .getUser()
                                .getUserInfo()
                                .getCurrentRoom()
                                .sendComposer(new RoomUserDataComposer(gameClient.getUser()).compose());
                        return true;
                    }
                }

            } catch (Exception e) {

            }
        }

        return false;
    }
}
