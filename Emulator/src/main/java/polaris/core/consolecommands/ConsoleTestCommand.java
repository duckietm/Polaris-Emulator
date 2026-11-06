package polaris.core.consolecommands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.users.User;

public class ConsoleTestCommand extends ConsoleCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConsoleTestCommand.class);

    public ConsoleTestCommand() {
        super("test", "This is just a test.");
    }

    @Override
    public void handle(String[] args) throws Exception {
        if (Emulator.debugging) {
            LOGGER.info("This is a test command for live debugging.");

            // AchievementManager.progressAchievement(4,
            // Emulator.getGameEnvironment().getAchievementManager().getAchievement("AllTimeHotelPresence"), 30);
            User user = Emulator.getGameEnvironment().getUserManager().getUser(1);
            user.getUserInfo().getMachineID();
        }
    }
}
