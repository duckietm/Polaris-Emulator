package com.eu.habbo.habbohotel.messenger;

import java.sql.ResultSet;
import java.sql.SQLException;

public record FriendRequest(int id, String username, String look) {

    public FriendRequest(ResultSet set) throws SQLException {
        this(set.getInt("id"), set.getString("username"), set.getString("look"));
    }
}
