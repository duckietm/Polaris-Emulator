package polaris.core.consolecommands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;

public class ShowRCONCommands extends ConsoleCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShowRCONCommands.class);

    public ShowRCONCommands() {
        super("rconcommands", "Show a list of all RCON commands");
    }

    @Override
    public void handle(String[] args) throws Exception {
        for (String command : Emulator.getRconServer().getCommands()) {
            LOGGER.info(command);
        }
    }
}
