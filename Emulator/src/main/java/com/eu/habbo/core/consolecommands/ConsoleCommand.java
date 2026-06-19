package com.eu.habbo.core.consolecommands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public abstract class ConsoleCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConsoleCommand.class);
    private static final Map<String, ConsoleCommand> commands = new HashMap<>();

    public final String key;
    public final String usage;

    public ConsoleCommand(String key, String usage) {
        this.key = key;
        this.usage = usage;
    }

    public static void load() {
        addCommand(new ConsoleShutdownCommand());
        addCommand(new ConsoleInfoCommand());
        addCommand(new ConsoleTestCommand());
        addCommand(new ShowInteractionsCommand());
        addCommand(new ShowRCONCommands());
        addCommand(new ThankyouArcturusCommand());
    }

    public static void addCommand(ConsoleCommand command) {
        commands.put(command.key, command);
    }

    public static ConsoleCommand findCommand(String key) {
        return commands.get(key);
    }

    public static boolean handle(String line) {
        String[] message = line.split(" ");

        if (message.length > 0) {
            ConsoleCommand command = ConsoleCommand.findCommand(message[0]);

            if (command != null) {
                try {
                    command.handle(message);
                    return true;
                } catch (Exception e) {
                    LOGGER.error("Caught exception", e);
                }
            } else {
                LOGGER.info("Unknown Console Command {}", message[0]);
                LOGGER.info("Commands Available ({}): ", commands.size());

                for (ConsoleCommand c : commands.values()) {
                    LOGGER.info("{} - {}", c.key, c.usage);
                }
            }
        }

        return false;
    }

    public abstract void handle(String[] args) throws Exception;
}
