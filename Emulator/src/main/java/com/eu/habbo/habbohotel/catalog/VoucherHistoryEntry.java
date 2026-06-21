package com.eu.habbo.habbohotel.catalog;

import java.sql.ResultSet;
import java.sql.SQLException;

public record VoucherHistoryEntry(int voucherId, int userId, int timestamp) {

    public VoucherHistoryEntry(ResultSet set) throws SQLException {
        this(set.getInt("voucher_id"), set.getInt("user_id"), set.getInt("timestamp"));
    }
}
