package polaris.hotel.catalog;

import java.sql.ResultSet;
import java.sql.SQLException;

public class VoucherHistoryEntry {
    private final int voucherId;
    private final int userId;
    private final int timestamp;

    public VoucherHistoryEntry(ResultSet set) throws SQLException {
        this.voucherId = set.getInt("voucher_id");
        this.userId = set.getInt("user_id");
        this.timestamp = set.getInt("timestamp");
    }

    public VoucherHistoryEntry(int voucherId, int userId, int timestamp) {
        this.voucherId = voucherId;
        this.userId = userId;
        this.timestamp = timestamp;
    }

    public int getVoucherId() {
        return voucherId;
    }

    public int getUserId() {
        return userId;
    }

    public int getTimestamp() {
        return timestamp;
    }
}
