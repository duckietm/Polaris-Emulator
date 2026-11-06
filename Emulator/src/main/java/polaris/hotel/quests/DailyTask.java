package polaris.hotel.quests;

import java.sql.ResultSet;
import java.sql.SQLException;

/** A daily task definition, one row of `daily_tasks`. Texts: daily_tasks.&lt;code&gt;.name/.desc/.hint. */
public final class DailyTask {
    private final int id;
    private final String code;
    private final QuestGoalType goalType;
    private final String goalData;
    private final int requiredRepeats;
    private final boolean bonus;
    private final String rewardType;
    private final String rewardExtra;
    private final int rewardAmount;
    private final String catalogName;
    private final String imageVersion;
    private final int sortOrder;

    public DailyTask(
            int id,
            String code,
            QuestGoalType goalType,
            String goalData,
            int requiredRepeats,
            boolean bonus,
            String rewardType,
            String rewardExtra,
            int rewardAmount,
            String catalogName,
            String imageVersion,
            int sortOrder) {
        this.id = id;
        this.code = code;
        this.goalType = goalType;
        this.goalData = goalData == null ? "" : goalData;
        this.requiredRepeats = Math.max(1, requiredRepeats);
        this.bonus = bonus;
        this.rewardType = rewardType == null ? QuestRewards.TYPE_DUCKETS : rewardType;
        this.rewardExtra = rewardExtra == null ? "" : rewardExtra;
        this.rewardAmount = rewardAmount;
        this.catalogName = catalogName == null ? "" : catalogName;
        this.imageVersion = imageVersion == null ? "" : imageVersion;
        this.sortOrder = sortOrder;
    }

    public static DailyTask fromResultSet(ResultSet set) throws SQLException {
        return new DailyTask(
                set.getInt("id"),
                set.getString("code"),
                QuestGoalType.fromCode(set.getString("goal_type")),
                set.getString("goal_data"),
                set.getInt("required_repeats"),
                set.getBoolean("is_bonus"),
                set.getString("reward_type"),
                set.getString("reward_extra"),
                set.getInt("reward_amount"),
                set.getString("catalog_name"),
                set.getString("image_version"),
                set.getInt("sort_order"));
    }

    public int getId() {
        return this.id;
    }

    public String getCode() {
        return this.code;
    }

    public QuestGoalType getGoalType() {
        return this.goalType;
    }

    public String getGoalData() {
        return this.goalData;
    }

    public int getRequiredRepeats() {
        return this.requiredRepeats;
    }

    public boolean isBonus() {
        return this.bonus;
    }

    public String getRewardType() {
        return this.rewardType;
    }

    public String getRewardExtra() {
        return this.rewardExtra;
    }

    public int getRewardAmount() {
        return this.rewardAmount;
    }

    public String getCatalogName() {
        return this.catalogName;
    }

    public String getImageVersion() {
        return this.imageVersion;
    }

    public int getSortOrder() {
        return this.sortOrder;
    }
}
