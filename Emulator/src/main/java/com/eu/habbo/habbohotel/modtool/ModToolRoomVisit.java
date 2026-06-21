package com.eu.habbo.habbohotel.modtool;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

public class ModToolRoomVisit implements Comparable<ModToolRoomVisit> {
    public int roomId;
    public String roomName;
    public int timestamp;
    public int exitTimestamp;
    public Set<ModToolChatLog> chat;

    public ModToolRoomVisit(ResultSet set) throws SQLException {
        this.roomId = set.getInt("room_id");
        this.roomName = set.getString("name");
        this.timestamp = set.getInt("timestamp");
    }

    public ModToolRoomVisit(int roomId, String roomName, int timestamp, int exitTimestamp) {
        this.roomId = roomId;
        this.roomName = roomName;
        this.timestamp = timestamp;
        this.exitTimestamp = exitTimestamp;
        this.chat = new HashSet<>();
    }

    @Override
    public int compareTo(ModToolRoomVisit o) {
        return o.timestamp - this.timestamp;
    }
}
