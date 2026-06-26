package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.ServerMessage;

public class TestCommand extends Command {
    public TestCommand() {
        super("acc_debug", new String[]{"test"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (gameClient.getHabbo() != null || !gameClient.getHabbo().hasPermission(Permission.ACC_SUPPORTTOOL) || !Emulator.debugging)
            return false;

        int header = Integer.parseInt(params[1]);

        var message = new ServerMessage(header);

        for (int i = 1; i < params.length; i++) {
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
