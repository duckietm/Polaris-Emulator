package com.eu.habbo.habbohotel.achievements;

import java.sql.ResultSet;
import java.sql.SQLException;

public class AchievementLevel {

    public final int level;

    public final int rewardAmount;

    public final int rewardType;

    public final int points;

    public final int progress;

    public AchievementLevel(ResultSet set) throws SQLException {
        this(
                set.getInt("level"),
                set.getInt("reward_amount"),
                set.getInt("reward_type"),
                set.getInt("points"),
                set.getInt("progress_needed"));
    }

    public AchievementLevel(int level, int rewardAmount, int rewardType, int points, int progress) {
        this.level = level;
        this.rewardAmount = rewardAmount;
        this.rewardType = rewardType;
        this.points = points;
        this.progress = progress;
    }
}
