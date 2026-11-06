package polaris.threading.runnables;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.achievements.Achievement;
import polaris.hotel.achievements.AchievementManager;
import polaris.hotel.users.User;

public class AchievementUpdater implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(AchievementUpdater.class);

    public static final int INTERVAL = 5 * 60;
    public int lastExecutionTimestamp = Emulator.getIntUnixTimestamp();

    @Override
    public void run() {
        if (!Emulator.isShuttingDown) {
            Emulator.getThreading().run(this, INTERVAL * 1000);
        }

        if (Emulator.isReady) {
            Achievement onlineTime =
                    Emulator.getGameEnvironment().getAchievementManager().getAchievement("AllTimeHotelPresence");

            // A hotel without the achievement has nothing to progress.
            if (onlineTime == null) return;

            int timestamp = Emulator.getIntUnixTimestamp();
            for (Map.Entry<Integer, User> set : Emulator.getGameEnvironment()
                    .getUserManager()
                    .getOnlineUsers()
                    .entrySet()) {
                // One user that fails is logged and skipped: the loop used to stop there, and every user after it
                // went without the online-time progress until the next run.
                try {
                    User user = set.getValue();
                    if (user == null || user.getUserStats() == null) continue;

                    int minutes = user.getUserStats().getOnlineMinutes(timestamp);
                    int progress = Math.max(0, user.getUserStats().getAchievementProgress(onlineTime));
                    if (minutes > progress) {
                        AchievementManager.progressAchievement(user, onlineTime, minutes - progress);
                    }
                } catch (RuntimeException e) {
                    LOGGER.error("Online-time achievement failed for user {}", set.getKey(), e);
                }
            }

            this.lastExecutionTimestamp = timestamp;
        }
    }
}
