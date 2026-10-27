package com.eu.habbo.habbohotel.achievements;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TalentTrackLevel {
    private static final Logger LOGGER = LoggerFactory.getLogger(TalentTrackLevel.class);

    public TalentTrackType type;
    public int level;
    public Map<Achievement, Integer> achievements;
    public Set<Item> items;
    public String[] perks;
    public String[] badges;
    public int hcDays;
    /** True when a configured task could not be found: such a level can never be completed. */
    public boolean missingAchievements;

    public TalentTrackLevel(ResultSet set) throws SQLException {
        this.type = TalentTrackType.valueOf(set.getString("type").toUpperCase());
        this.level = set.getInt("level");
        try {
            this.hcDays = set.getInt("reward_hc_days");
        } catch (SQLException missingMetadata) {
            this.hcDays = 0;
        }
        this.achievements = new HashMap<>();
        this.items = new HashSet<>();

        String[] achievements = set.getString("achievement_ids").split(",");
        String[] achievementLevels = set.getString("achievement_levels").split(",");
        if (achievementLevels.length == achievements.length) {
            AchievementManager manager = Emulator.getGameEnvironment().getAchievementManager();
            for (int i = 0; i < achievements.length; i++) {
                String task = achievements[i].trim();
                if (task.isEmpty() || achievementLevels[i].isBlank()) continue;

                Achievement achievement = resolveAchievement(manager, task);

                if (achievement != null) {
                    this.achievements.put(achievement, Integer.parseInt(achievementLevels[i].trim()));
                } else {
                    this.missingAchievements = true;
                    LOGGER.error(
                            "Could not find achievement {} for talenttrack level {} of type {}",
                            task,
                            this.level,
                            this.type);
                }
            }
        } else {
            this.missingAchievements = true;
            LOGGER.error(
                    "Talent track level {} of type {} lists {} achievements but {} levels",
                    this.level,
                    this.type,
                    achievements.length,
                    achievementLevels.length);
        }

        for (String s : set.getString("reward_furni").split(",")) {
            if (s.isBlank()) continue;
            Item item = Emulator.getGameEnvironment().getItemManager().getItem(Integer.parseInt(s.trim()));

            if (item != null) {
                this.items.add(item);
            } else {
                LOGGER.error("Incorrect reward furni (ID: {}) for talent track level {}", s, this.level);
            }
        }

        if (!set.getString("reward_perks").isEmpty()) {
            this.perks = set.getString("reward_perks").split(",");
        }

        if (!set.getString("reward_badges").isEmpty()) {
            this.badges = set.getString("reward_badges").split(",");
        }
    }

    /**
     * A task is an achievement id, or its name (with or without "ACH_"), so the rows do not depend on how a
     * hotel's achievements table is numbered.
     */
    static Achievement resolveAchievement(AchievementManager manager, String task) {
        if (manager == null || task == null || task.isEmpty()) return null;

        if (task.chars().allMatch(Character::isDigit)) {
            return manager.getAchievement(Integer.parseInt(task));
        }

        String name = task.regionMatches(true, 0, "ACH_", 0, 4) ? task.substring(4) : task;
        return manager.getAchievement(name);
    }
}
