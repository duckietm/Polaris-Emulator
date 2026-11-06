package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.permissions.Permission;
import polaris.messages.ServerMessage;

public class TestCommand extends Command {
    public TestCommand() {
        super("acc_debug", new String[] {"test"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        // The check read "!= null", so the command never ran for anyone.
        if (gameClient.getUser() == null
                || !gameClient.getUser().hasPermission(Permission.ACC_SUPPORTTOOL)
                || !Emulator.debugging) return false;

        if (params.length < 2) return false;

        int header;
        try {
            header = Integer.parseInt(params[1]);
        } catch (NumberFormatException e) {
            return false;
        }

        ServerMessage message = new ServerMessage(header);

        // params[1] is the header; the fields follow it.
        for (int i = 2; i < params.length; i++) {
            String[] data = params[i].split(":");

            if (data[0].equalsIgnoreCase("b")) {
                message.appendBoolean(data[1].equalsIgnoreCase("1"));
            } else if (data[0].equalsIgnoreCase("s")) {
                if (data.length > 1) {
                    message.appendString(data[1]);
                } else {
                    message.appendString("");
                }
            } else if (data[0].equals("i")) {
                message.appendInt(Integer.valueOf(data[1]));
            } else if (data[0].equalsIgnoreCase("by")) {
                message.appendByte(Integer.valueOf(data[1]));
            } else if (data[0].equalsIgnoreCase("sh")) {
                message.appendShort(Integer.parseInt(data[1]));
            }
        }

        gameClient.sendResponse(message);

        return true;
    }
}
