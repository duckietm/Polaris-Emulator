package polaris.hotel.quests;

import java.sql.ResultSet;
import java.sql.SQLException;

/** A quest definition, one row of `quests`. Texts: quests.&lt;campaign&gt;.&lt;code&gt;.name/.desc/.hint/.completed. */
public final class Quest {
    /** activityPointType of a credits reward, the official client maps -1 to the credits icon. */
    public static final int REWARD_CREDITS = -1;

    public static final int REWARD_DUCKETS = 0;

    private final int id;
    private final String campaignCode;
    private final String code;
    private final String chainCode;
    private final int sortOrder;
    private final QuestGoalType goalType;
    private final String goalData;
    private final int goalCount;
    private final int rewardType;
    private final int rewardAmount;
    private final String rewardBadge;
    private final boolean easy;
    private final boolean daily;
    private final int seasonalSeconds;
    private final int waitSeconds;
    private final String catalogPageName;
    private final String imageVersion;

    public Quest(
            int id,
            String campaignCode,
            String code,
            String chainCode,
            int sortOrder,
            QuestGoalType goalType,
            String goalData,
            int goalCount,
            int rewardType,
            int rewardAmount,
            String rewardBadge,
            boolean easy,
            boolean daily,
            int seasonalSeconds,
            int waitSeconds,
            String catalogPageName,
            String imageVersion) {
        this.id = id;
        this.campaignCode = campaignCode;
        this.code = code;
        this.chainCode = chainCode == null ? "" : chainCode;
        this.sortOrder = sortOrder;
        this.goalType = goalType;
        this.goalData = goalData == null ? "" : goalData;
        this.goalCount = Math.max(1, goalCount);
        this.rewardType = rewardType;
        this.rewardAmount = rewardAmount;
        this.rewardBadge = rewardBadge == null ? "" : rewardBadge;
        this.easy = easy;
        this.daily = daily;
        this.seasonalSeconds = seasonalSeconds;
        this.waitSeconds = waitSeconds;
        this.catalogPageName = catalogPageName == null ? "" : catalogPageName;
        this.imageVersion = imageVersion == null ? "" : imageVersion;
    }

    public static Quest fromResultSet(ResultSet set) throws SQLException {
        return new Quest(
                set.getInt("id"),
                set.getString("campaign_code"),
                set.getString("code"),
                set.getString("chain_code"),
                set.getInt("sort_order"),
                QuestGoalType.fromCode(set.getString("goal_type")),
                set.getString("goal_data"),
                set.getInt("goal_count"),
                set.getInt("reward_type"),
                set.getInt("reward_amount"),
                set.getString("reward_badge"),
                set.getBoolean("easy"),
                set.getBoolean("daily"),
                set.getInt("seasonal_seconds"),
                set.getInt("wait_seconds"),
                set.getString("catalog_page_name"),
                set.getString("image_version"));
    }

    public int getId() {
        return this.id;
    }

    public String getCampaignCode() {
        return this.campaignCode;
    }

    public String getCode() {
        return this.code;
    }

    public String getChainCode() {
        return this.chainCode;
    }

    public int getSortOrder() {
        return this.sortOrder;
    }

    public QuestGoalType getGoalType() {
        return this.goalType;
    }

    public String getGoalData() {
        return this.goalData;
    }

    public int getGoalCount() {
        return this.goalCount;
    }

    public int getRewardType() {
        return this.rewardType;
    }

    public int getRewardAmount() {
        return this.rewardAmount;
    }

    public String getRewardBadge() {
        return this.rewardBadge;
    }

    public boolean isEasy() {
        return this.easy;
    }

    public boolean isDaily() {
        return this.daily;
    }

    public boolean isSeasonal() {
        return this.seasonalSeconds > 0;
    }

    public int getSeasonalSeconds() {
        return this.seasonalSeconds;
    }

    public int getWaitSeconds() {
        return this.waitSeconds;
    }

    public String getCatalogPageName() {
        return this.catalogPageName;
    }

    public String getImageVersion() {
        return this.imageVersion;
    }

    /** The official quest "type" string; the client only inspects it for the catalog link. */
    public String getTypeCode() {
        return this.goalType == QuestGoalType.PLACE_FURNI ? "PLACE_ITEM" : String.valueOf(this.goalType);
    }
}
