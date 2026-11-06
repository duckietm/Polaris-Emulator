package polaris.hotel.quests;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.GameEnvironment;
import polaris.hotel.users.User;

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

    public static void progress(User user, QuestGoalType goalType, int amount) {
        if (user == null || user.getClient() == null || goalType == null || amount < 1) {
            return;
        }
        GameEnvironment environment = environment();
        if (environment == null) {
            return;
        }
        try {
            QuestManager quests = environment.getQuestManager();
            if (quests != null) {
                quests.progress(user, goalType, amount);
            }
            DailyTaskManager dailyTasks = environment.getDailyTaskManager();
            if (dailyTasks != null) {
                dailyTasks.progress(user, goalType, amount);
            }
            RewardTrackManager rewardTracks = environment.getRewardTrackManager();
            if (rewardTracks != null) {
                rewardTracks.progress(user, goalType, amount);
            }
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Quest progress {} failed for {}",
                    goalType,
                    user.getUserInfo().getUsername(),
                    exception);
        }
    }

    /** Progress for a user by id, when they are online (a pet's owner, say). */
    public static void progress(int userId, QuestGoalType goalType, int amount) {
        GameEnvironment environment = environment();
        if (environment == null || environment.getUserManager() == null) {
            return;
        }
        progress(environment.getUserManager().getUser(userId), goalType, amount);
    }

    /** A campaign or daily quest was completed: the daily tasks and the reward track count it. */
    public static void questCompleted(User user) {
        GameEnvironment environment = environment();
        if (environment == null) {
            return;
        }
        try {
            DailyTaskManager dailyTasks = environment.getDailyTaskManager();
            if (dailyTasks != null) {
                dailyTasks.progress(user, QuestGoalType.COMPLETE_QUEST, 1);
            }
            RewardTrackManager rewardTracks = environment.getRewardTrackManager();
            if (rewardTracks != null) {
                rewardTracks.progress(user, QuestGoalType.COMPLETE_QUEST, 1);
            }
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Quest completion fan-out failed for {}", user.getUserInfo().getUsername(), exception);
        }
    }

    /** A daily task reward was claimed: the bonus task and the reward track count it. */
    public static void dailyTaskClaimed(User user) {
        GameEnvironment environment = environment();
        if (environment == null) {
            return;
        }
        try {
            DailyTaskManager dailyTasks = environment.getDailyTaskManager();
            if (dailyTasks != null) {
                dailyTasks.progress(user, QuestGoalType.CLAIM_DAILY_TASK, 1);
            }
            RewardTrackManager rewardTracks = environment.getRewardTrackManager();
            if (rewardTracks != null) {
                rewardTracks.progress(user, QuestGoalType.CLAIM_DAILY_TASK, 1);
            }
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Daily task claim fan-out failed for {}", user.getUserInfo().getUsername(), exception);
        }
    }

    /** A user logged in: the reward tracks are pushed, the official client never asks for them. */
    public static void loggedIn(User user) {
        GameEnvironment environment = environment();
        if (environment != null && environment.getRewardTrackManager() != null) {
            environment.getRewardTrackManager().sendRewardTracksOnLogin(user);
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
