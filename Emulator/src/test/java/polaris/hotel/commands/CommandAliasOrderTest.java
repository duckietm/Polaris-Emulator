package polaris.hotel.commands;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import polaris.hotel.gameclients.GameClient;

/** Two commands with the same alias: which one ran depended on the hash order of their class names. */
class CommandAliasOrderTest {

    static class FirstAliasCommand extends Command {
        FirstAliasCommand() {
            super(null, new String[] {"aliasordertest"});
        }

        @Override
        public boolean handle(GameClient gameClient, String[] params) {
            return true;
        }
    }

    static class SecondAliasCommand extends Command {
        SecondAliasCommand() {
            super(null, new String[] {"AliasOrderTest"});
        }

        @Override
        public boolean handle(GameClient gameClient, String[] params) {
            return true;
        }
    }

    @Test
    void theCommandRegisteredLastRuns() {
        CommandHandler.addCommand(new FirstAliasCommand());
        Command second = new SecondAliasCommand();
        CommandHandler.addCommand(second);

        assertSame(second, CommandHandler.getCommand("aliasordertest"));
    }

    @Test
    void theRemovedCommandsAreNotRegisteredAndPluginsHasItsPermission() throws Exception {
        Path commands = Path.of("src/main/java/polaris/hotel/commands");
        String handler = Files.readString(commands.resolve("CommandHandler.java"));

        assertTrue(!handler.contains("addCommand(new TrashCommand());"));
        assertTrue(Files.readString(commands.resolve("PluginsCommand.java")).contains("\"cmd_plugins\","));
    }
}
