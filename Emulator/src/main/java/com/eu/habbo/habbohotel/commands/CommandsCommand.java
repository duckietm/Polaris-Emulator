package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import java.util.List;

public class CommandsCommand extends Command {
    public CommandsCommand() {
        super(
                "cmd_commands",
                Emulator.getTexts().getValue("commands.keys.cmd_commands").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        StringBuilder message = new StringBuilder(Emulator.getTexts().getValue("commands.generic.cmd_commands.text"));
        List<Command> commands = Emulator.getGameEnvironment()
                .getCommandHandler()
                .getCommandsForRank(
                        gameClient.getHabbo().getHabboInfo().getRank().getId());
        message.append("(").append(commands.size()).append("):\r\n");

        for (Command c : commands) {
            message.append(formatEntry(
                    ":" + c.keys[0],
                    text("commands.description." + c.permission),
                    text("commands.help." + c.permission)));
        }

        gameClient.getHabbo().alert(new String[] {message.toString()});

        return true;
    }

    private static String text(String textKey) {
        String value = Emulator.getTexts().getValueQuietly(textKey, "");

        return value.equals(textKey) ? "" : value;
    }

    /**
     * Builds the lines for one command. {@code commands.description.*} usually holds the usage
     * line - it starts with ':' and replaces the plain command - while {@code commands.help.*}
     * holds the sentence describing what the command does. A description already written into
     * {@code commands.description.*} still wins, so hotels keep any wording they customised.
     */
    static String formatEntry(String fallbackCommandLine, String descriptionText, String helpText) {
        String commandLine = fallbackCommandLine;
        String description = "";

        if (descriptionText.startsWith(":")) {
            commandLine = descriptionText;
        } else if (!descriptionText.isEmpty()) {
            description = descriptionText;
        }

        if (description.isEmpty()) {
            description = helpText;
        }

        return description.isEmpty() ? commandLine + "\r" : commandLine + "\r" + description + "\r";
    }
}
