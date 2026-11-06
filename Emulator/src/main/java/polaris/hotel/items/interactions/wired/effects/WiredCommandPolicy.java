package polaris.hotel.items.interactions.wired.effects;

import java.util.Locale;
import java.util.Set;
import polaris.hotel.commands.Command;
import polaris.hotel.commands.CommandHandler;
import polaris.hotel.commands.DanceCommand;
import polaris.hotel.commands.HandItemCommand;
import polaris.hotel.commands.LayCommand;
import polaris.hotel.commands.MoonwalkCommand;
import polaris.hotel.commands.SitCommand;
import polaris.hotel.commands.StandCommand;
import polaris.hotel.permissions.Permission;
import polaris.hotel.users.User;

/** Authorization boundary for commands invoked by wired rather than typed by a user. */
final class WiredCommandPolicy {
    private static final Set<Class<? extends Command>> SAFE_SELF_COMMANDS = Set.of(
            SitCommand.class,
            StandCommand.class,
            LayCommand.class,
            DanceCommand.class,
            MoonwalkCommand.class,
            HandItemCommand.class);

    private WiredCommandPolicy() {}

    static Command resolve(String commandLine) {
        String key = commandKey(commandLine);
        return key.isEmpty() ? null : CommandHandler.getCommand(key);
    }

    static boolean canUse(Command command, User principal) {
        if (command == null) return false;

        boolean hasCommandPermission =
                command.permission == null || (principal != null && principal.hasPermission(command.permission));
        if (!hasCommandPermission) return false;

        return isSafeCommandType(command.getClass())
                || (principal != null && principal.hasPermission(Permission.ACC_SUPERWIRED));
    }

    static boolean isSafeCommandType(Class<?> type) {
        return type != null && SAFE_SELF_COMMANDS.contains(type);
    }

    static String commandKey(String commandLine) {
        if (commandLine == null) return "";
        String normalized = commandLine.trim();
        if (normalized.startsWith(":")) normalized = normalized.substring(1).trim();
        int separator = normalized.indexOf(' ');
        String key = separator < 0 ? normalized : normalized.substring(0, separator);
        return key.toLowerCase(Locale.ROOT);
    }
}
