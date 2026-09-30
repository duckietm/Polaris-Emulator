package com.eu.habbo.habbohotel.quests;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.users.Habbo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The single hook the rest of the emulator calls when a user does something the quest engine counts.
 * Fans out to the quests, the daily tasks and the reward track; never lets a quest failure break the
 * gameplay that triggered it.
 */
public final class QuestProgressEvents {
    private static final Logger LOGGER = LoggerFactory.getLogger(QuestProgressEvents.class);

    private QuestProgressEvents() {}

    private static GameEnvironment environment() {
        return Emulator.getGameEnvironment();
    }

    public static void progress(Habbo habbo, QuestGoalType goalType, int amount) {
        if (habbo == null || habbo.getClient() == null || goalType == null || amount < 1) {
            return;
        }
        GameEnvironment environment = environment();
        if (environment == null) {
            return;
        }
        try {
            QuestManager quests = environment.getQuestManager();
            if (quests != null) {
                quests.progress(habbo, goalType, amount);
            }
            DailyTaskManager dailyTasks = environment.getDailyTaskManager();
            if (dailyTasks != null) {
                dailyTasks.progress(habbo, goalType, amount);
            }
            RewardTrackManager rewardTracks = environment.getRewardTrackManager();
            if (rewardTracks != null) {
                rewardTracks.progress(habbo, goalType, amount);
            }
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Quest progress {} failed for {}",
                    goalType,
                    habbo.getHabboInfo().getUsername(),
                    exception);
        }
    }

    /** Progress for a user by id, when they are online (a pet's owner, say). */
    public static void progress(int userId, QuestGoalType goalType, int amount) {
        GameEnvironment environment = environment();
        if (environment == null || environment.getHabboManager() == null) {
            return;
        }
        progress(environment.getHabboManager().getHabbo(userId), goalType, amount);
    }

    /** A campaign or daily quest was completed: the daily tasks and the reward track count it. */
    public static void questCompleted(Habbo habbo) {
        GameEnvironment environment = environment();
        if (environment == null) {
            return;
        }
        try {
            DailyTaskManager dailyTasks = environment.getDailyTaskManager();
            if (dailyTasks != null) {
                dailyTasks.progress(habbo, QuestGoalType.COMPLETE_QUEST, 1);
            }
            RewardTrackManager rewardTracks = environment.getRewardTrackManager();
            if (rewardTracks != null) {
                rewardTracks.progress(habbo, QuestGoalType.COMPLETE_QUEST, 1);
            }
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Quest completion fan-out failed for {}",
                    habbo.getHabboInfo().getUsername(),
                    exception);
        }
    }

    /** A daily task reward was claimed: the bonus task and the reward track count it. */
    public static void dailyTaskClaimed(Habbo habbo) {
        GameEnvironment environment = environment();
        if (environment == null) {
            return;
        }
        try {
            DailyTaskManager dailyTasks = environment.getDailyTaskManager();
            if (dailyTasks != null) {
                dailyTasks.progress(habbo, QuestGoalType.CLAIM_DAILY_TASK, 1);
            }
            RewardTrackManager rewardTracks = environment.getRewardTrackManager();
            if (rewardTracks != null) {
                rewardTracks.progress(habbo, QuestGoalType.CLAIM_DAILY_TASK, 1);
            }
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Daily task claim fan-out failed for {}",
                    habbo.getHabboInfo().getUsername(),
                    exception);
        }
    }

    /** Drops the cached state of a user who left. */
    public static void unload(int userId) {
        GameEnvironment environment = environment();
        if (environment == null) {
            return;
        }
        if (environment.getQuestManager() != null) {
            environment.getQuestManager().unload(userId);
        }
        if (environment.getDailyTaskManager() != null) {
            environment.getDailyTaskManager().unload(userId);
        }
        if (environment.getRewardTrackManager() != null) {
            environment.getRewardTrackManager().unload(userId);
        }
    }
}
