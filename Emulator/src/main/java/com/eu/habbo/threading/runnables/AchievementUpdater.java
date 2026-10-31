package com.eu.habbo.threading.runnables;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.Achievement;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.users.Habbo;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
            for (Map.Entry<Integer, Habbo> set : Emulator.getGameEnvironment()
                    .getHabboManager()
                    .getOnlineHabbos()
                    .entrySet()) {
                // One user that fails is logged and skipped: the loop used to stop there, and every user after it
                // went without the online-time progress until the next run.
                try {
                    Habbo habbo = set.getValue();
                    if (habbo == null || habbo.getHabboStats() == null) continue;

                    int minutes = habbo.getHabboStats().getOnlineMinutes(timestamp);
                    int progress = Math.max(0, habbo.getHabboStats().getAchievementProgress(onlineTime));
                    if (minutes > progress) {
                        AchievementManager.progressAchievement(habbo, onlineTime, minutes - progress);
                    }
                } catch (RuntimeException e) {
                    LOGGER.error("Online-time achievement failed for user {}", set.getKey(), e);
                }
            }

            this.lastExecutionTimestamp = timestamp;
        }
    }
}
