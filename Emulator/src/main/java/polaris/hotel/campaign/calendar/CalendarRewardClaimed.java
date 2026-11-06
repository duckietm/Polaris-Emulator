package polaris.hotel.campaign.calendar;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

public class CalendarRewardClaimed {
    private final int user_id;
    private final int campaign;
    private final int day;
    private final int reward_id;
    private final Timestamp timestamp;

    public CalendarRewardClaimed(ResultSet set) throws SQLException {
        this.user_id = set.getInt("user_id");
        this.campaign = set.getInt("campaign_id");
        this.day = set.getInt("day");
        this.reward_id = set.getInt("reward_id");
        this.timestamp = new Timestamp(set.getInt("timestamp") * 1000L);
    }

    public CalendarRewardClaimed(int user_id, int campaign, int day, int reward_id, Timestamp timestamp) {
        this.user_id = user_id;
        this.campaign = campaign;
        this.day = day;
        this.reward_id = reward_id;
        this.timestamp = timestamp;
    }

    public int getUserId() {
        return this.user_id;
    }

    public int getCampaignId() {
        return this.campaign;
    }

    public int getDay() {
        return this.day;
    }

    public int getRewardId() {
        return this.reward_id;
    }

    public Timestamp getTimestamp() {
        return this.timestamp;
    }
}
