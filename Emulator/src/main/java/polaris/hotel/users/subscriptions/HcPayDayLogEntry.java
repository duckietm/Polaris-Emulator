package polaris.hotel.users.subscriptions;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import polaris.Emulator;
import polaris.core.DatabaseLoggable;

/**
 * @author Beny
 */
public class HcPayDayLogEntry implements Runnable, DatabaseLoggable {

    private static final String QUERY =
            "INSERT INTO `logs_hc_payday` (`timestamp`, `user_id`, `hc_streak`, `total_coins_spent`, `reward_coins_spent`, `reward_streak`, `total_payout`, `currency`, `claimed`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    public final int timestamp;
    public final int userId;
    public final int hcStreak;
    public final int totalCoinsSpent;
    public final int rewardCoinsSpent;
    public final int rewardStreak;
    public final int totalPayout;
    public final String currency;
    public final boolean claimed;

    public HcPayDayLogEntry(
            int timestamp,
            int userId,
            int hcStreak,
            int totalCoinsSpent,
            int rewardCoinsSpent,
            int rewardStreak,
            int totalPayout,
            String currency,
            boolean claimed) {
        this.timestamp = timestamp;
        this.userId = userId;
        this.hcStreak = hcStreak;
        this.totalCoinsSpent = totalCoinsSpent;
        this.rewardCoinsSpent = rewardCoinsSpent;
        this.rewardStreak = rewardStreak;
        this.totalPayout = totalPayout;
        this.currency = currency;
        this.claimed = claimed;
    }

    @Override
    public String getQuery() {
        return QUERY;
    }

    @Override
    public void log(PreparedStatement statement) throws SQLException {
        statement.setInt(1, this.timestamp);
        statement.setInt(2, this.userId);
        statement.setInt(3, this.hcStreak);
        statement.setInt(4, this.totalCoinsSpent);
        statement.setInt(5, this.rewardCoinsSpent);
        statement.setInt(6, this.rewardStreak);
        statement.setInt(7, this.totalPayout);
        statement.setString(8, this.currency);
        statement.setInt(9, this.claimed ? 1 : 0);
        statement.addBatch();
    }

    @Override
    public void run() {
        Emulator.getDatabaseLogger().store(this);
    }
}
