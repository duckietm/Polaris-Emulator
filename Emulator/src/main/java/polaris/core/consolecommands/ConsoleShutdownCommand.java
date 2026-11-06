package polaris.core.consolecommands;

import polaris.hotel.commands.ShutdownCommand;

public class ConsoleShutdownCommand extends ConsoleCommand {
    public ConsoleShutdownCommand() {
        super("stop", "Stop the emulator.");
    }

    @Override
    public void handle(String[] args) throws Exception {
        new ShutdownCommand().handle(null, args);
    }
}
