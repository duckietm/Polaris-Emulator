package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;

import java.util.List;

public class CommandsCommand extends Command {
    public CommandsCommand() {
        super("cmd_commands", Emulator.getTexts().getValue("commands.keys.cmd_commands").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        StringBuilder message = new StringBuilder(Emulator.getTexts().getValue("commands.generic.cmd_commands.text"));
        List<Command> commands = Emulator.getGameEnvironment().getCommandHandler().getCommandsForRank(gameClient.getHabbo().getHabboInfo().getRank().getId());
        message.append("(").append(commands.size()).append("):\r\n");

        for (Command c : commands) {
            String textKey = "commands.description." + c.permission;
            String commandText = Emulator.getTexts().getValueQuietly(textKey, "");
            String commandLine = ":" + c.keys[0];
            String description = "";

            if (commandText.startsWith(":")) {
                commandLine = commandText;
            } else if (!commandText.isEmpty() && !commandText.equals(textKey)) {
                description = commandText;
            }

            message.append(commandLine).append("\r");

            if (!description.isEmpty()) {
                message.append(description).append("\r");
            }
        }

        gameClient.getHabbo().alert(new String[]{message.toString()});

        return true;
    }
}
