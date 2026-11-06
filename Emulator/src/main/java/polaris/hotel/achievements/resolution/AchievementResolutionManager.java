package polaris.hotel.achievements.resolution;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.achievements.Achievement;
import polaris.hotel.achievements.AchievementLevel;
import polaris.hotel.users.User;

/**
 * New Year resolutions: the owner of a resolution furni picks one of their own achievements and
 * promises to reach its next level before the clock runs out. The promise lives on the furni, one
 * row per piece of furniture, so it survives a restart and two furni can hold two promises.
 *
 * <p>Only achievements the owner has already started are offered, because the window shows the level
 * they are on next to the level they would have to reach. A single final instance keeps the class
 * free of mutable statics, like the other managers.
 */
public class AchievementResolutionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(AchievementResolutionManager.class);

    private static final int DAY_SECONDS = 86400;

    private static final AchievementResolutionManager INSTANCE = new AchievementResolutionManager();

    private AchievementResolutionManager() {}

    public static AchievementResolutionManager getInstance() {
        return INSTANCE;
    }

    /** How long a promise lasts once it is made. */
    public int durationSeconds() {
        return Math.max(1, Emulator.getConfig().getInt("hotel.resolution.days", 7)) * DAY_SECONDS;
    }

    public String badgeCode(Achievement achievement, int level) {
        return "ACH_" + achievement.name + level;
    }

    /**
     * The achievements the owner may promise: the ones they have already started. An achievement
     * whose levels are all done cannot be promised, and neither can one they already promised on
     * another furni that is still open.
     */
    public List<AchievementResolutionCandidate> candidates(User user) {
        List<AchievementResolutionCandidate> candidates = new ArrayList<>();

        if (user == null) return candidates;

        Set<Integer> promised = this.openPromises(user.getUserInfo().getId());

        for (Map.Entry<Achievement, Integer> entry :
                user.getUserStats().getAchievementProgress().entrySet()) {
            Achievement achievement = entry.getKey();

            if (achievement == null) continue;

            AchievementLevel current = achievement.getLevelForProgress(entry.getValue());
            int level = current == null ? 0 : current.level;
            AchievementLevel next = achievement.getNextLevel(level);

            int state = AchievementResolutionCandidate.ENABLED;

            if (next == null) {
                state = AchievementResolutionCandidate.ALL_LEVELS_DONE;
            } else if (promised.contains(achievement.id)) {
                state = AchievementResolutionCandidate.ALREADY_PROMISED;
            }

            int required = next == null ? level : next.level;

            candidates.add(new AchievementResolutionCandidate(
                    achievement.id, level, this.badgeCode(achievement, required), required, state));
        }

        return candidates;
    }

    private Set<Integer> openPromises(int userId) {
        Set<Integer> promised = new HashSet<>();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT achievement_id FROM achievement_resolutions WHERE user_id = ? AND completed_at = 0")) {
            statement.setInt(1, userId);

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    promised.add(set.getInt("achievement_id"));
                }
            }
        } catch (SQLException exception) {
            LOGGER.error("Caught SQL exception", exception);
        }

        return promised;
    }

    /** The promise a furni carries, or null. */
    public AchievementResolution resolution(int itemId) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT item_id, user_id, achievement_id, target_level, badge_code, started_at, ends_at,"
                                + " completed_at FROM achievement_resolutions WHERE item_id = ? LIMIT 1")) {
            statement.setInt(1, itemId);

            try (ResultSet set = statement.executeQuery()) {
                if (!set.next()) return null;

                return new AchievementResolution(
                        set.getInt("item_id"),
                        set.getInt("user_id"),
                        set.getInt("achievement_id"),
                        set.getInt("target_level"),
                        set.getString("badge_code"),
                        set.getInt("started_at"),
                        set.getInt("ends_at"),
                        set.getInt("completed_at"));
            }
        } catch (SQLException exception) {
            LOGGER.error("Caught SQL exception", exception);
            return null;
        }
    }

    /** Makes the promise. Returns it, or null when that achievement cannot be promised. */
    public AchievementResolution promise(User user, int itemId, int achievementId) {
        Achievement achievement =
                Emulator.getGameEnvironment().getAchievementManager().getAchievement(achievementId);

        if (user == null || achievement == null) return null;

        AchievementResolutionCandidate candidate = this.candidates(user).stream()
                .filter(entry -> entry.achievementId() == achievementId)
                .findFirst()
                .orElse(null);

        if (candidate == null || candidate.state() != AchievementResolutionCandidate.ENABLED) return null;

        int now = Emulator.getIntUnixTimestamp();
        AchievementResolution resolution = new AchievementResolution(
                itemId,
                user.getUserInfo().getId(),
                achievementId,
                candidate.requiredLevel(),
                candidate.badgeCode(),
                now,
                now + this.durationSeconds(),
                0);

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "REPLACE INTO achievement_resolutions (item_id, user_id, achievement_id, target_level,"
                                + " badge_code, started_at, ends_at, completed_at) VALUES (?, ?, ?, ?, ?, ?, ?, 0)")) {
            statement.setInt(1, resolution.itemId());
            statement.setInt(2, resolution.userId());
            statement.setInt(3, resolution.achievementId());
            statement.setInt(4, resolution.targetLevel());
            statement.setString(5, resolution.badgeCode());
            statement.setInt(6, resolution.startedAt());
            statement.setInt(7, resolution.endsAt());
            statement.execute();
        } catch (SQLException exception) {
            LOGGER.error("Caught SQL exception", exception);
            return null;
        }

        return resolution;
    }

    /** Drops the promise, which is what "re-select achievement" and a run-out clock both do. */
    public void clear(int itemId) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("DELETE FROM achievement_resolutions WHERE item_id = ?")) {
            statement.setInt(1, itemId);
            statement.execute();
        } catch (SQLException exception) {
            LOGGER.error("Caught SQL exception", exception);
        }
    }

    /** Writes down that the promise was kept. */
    public void complete(int itemId, int now) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE achievement_resolutions SET completed_at = ? WHERE item_id = ? AND completed_at = 0")) {
            statement.setInt(1, now);
            statement.setInt(2, itemId);
            statement.execute();
        } catch (SQLException exception) {
            LOGGER.error("Caught SQL exception", exception);
        }
    }

    /** Where the owner is on the promised achievement: what they have, and what the level asks for. */
    public int[] progress(User user, AchievementResolution resolution) {
        if (user == null || resolution == null) return new int[] {0, 0};

        Achievement achievement =
                Emulator.getGameEnvironment().getAchievementManager().getAchievement(resolution.achievementId());

        if (user == null || achievement == null) return new int[] {0, 0};

        int progress = user.getUserStats().getAchievementProgress(achievement);
        AchievementLevel target = achievement.getNextLevel(resolution.targetLevel() - 1);

        return new int[] {progress, target == null ? progress : target.progress};
    }

    /** True once the owner reached the level they promised. */
    public boolean kept(User user, AchievementResolution resolution) {
        if (user == null || resolution == null) return false;

        Achievement achievement =
                Emulator.getGameEnvironment().getAchievementManager().getAchievement(resolution.achievementId());

        if (user == null || achievement == null) return false;

        AchievementLevel current =
                achievement.getLevelForProgress(user.getUserStats().getAchievementProgress(achievement));

        return current != null && current.level >= resolution.targetLevel();
    }
}
