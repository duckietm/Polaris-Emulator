package com.eu.habbo.habbohotel.catalog;

import com.eu.habbo.Emulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class Voucher {
    private static final Logger LOGGER = LoggerFactory.getLogger(Voucher.class);

    public enum ClaimResult {
        CLAIMED,
        EXHAUSTED,
        USER_LIMIT,
        FAILED
    }

    public final int id;
    public final String code;
    public final int credits;
    public final int points;
    public final int pointsType;
    public final int catalogItemId;
    public final int amount;
    public final int limit;
    private final List<VoucherHistoryEntry> history = new ArrayList<>();

    public Voucher(ResultSet set) throws SQLException {
        this.id = set.getInt("id");
        this.code = set.getString("code");
        this.credits = set.getInt("credits");
        this.points = set.getInt("points");
        this.pointsType = set.getInt("points_type");
        this.catalogItemId = set.getInt("catalog_item_id");
        this.amount = set.getInt("amount");
        this.limit = set.getInt("limit");

        this.loadHistory();
    }

    private void loadHistory() {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT * FROM voucher_history WHERE voucher_id = ?")) {
            statement.setInt(1, this.id);
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    this.history.add(new VoucherHistoryEntry(set));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public boolean hasUserExhausted(int userId) {
        return this.limit > 0 && Math.toIntExact(this.history.stream().filter(h -> h.getUserId() == userId).count()) >= this.limit;
    }

    public boolean isExhausted() {
        return this.amount > 0 && this.history.size() >= this.amount;
    }

    public synchronized ClaimResult claimForUser(int userId) {
        if (this.isExhausted()) {
            return ClaimResult.EXHAUSTED;
        }

        if (this.hasUserExhausted(userId)) {
            return ClaimResult.USER_LIMIT;
        }

        int timestamp = Emulator.getIntUnixTimestamp();
        if (!this.insertHistoryEntry(userId, timestamp)) {
            return ClaimResult.FAILED;
        }

        this.history.add(new VoucherHistoryEntry(this.id, userId, timestamp));
        return ClaimResult.CLAIMED;
    }

    private boolean insertHistoryEntry(int userId, int timestamp) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("INSERT INTO voucher_history (`voucher_id`, `user_id`, `timestamp`) VALUES (?, ?, ?)")) {
            statement.setInt(1, this.id);
            statement.setInt(2, userId);
            statement.setInt(3, timestamp);

            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
            return false;
        }
    }
}
