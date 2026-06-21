package com.eu.habbo.habbohotel.mentions;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;

import java.sql.ResultSet;
import java.sql.SQLException;

public record HabboMention(
        int id,
        int targetUserId,
        int senderUserId,
        String senderUsername,
        int roomId,
        String roomName,
        String message,
        int mentionType,
        int timestamp,
        boolean read,
        String senderFigure
) {

    public static final int TYPE_DIRECT = 0;
    public static final int TYPE_ROOM = 1;

    public HabboMention {
        senderFigure = senderFigure == null ? "" : senderFigure;
    }

    public HabboMention(ResultSet set) throws SQLException {
        this(
                set.getInt("id"),
                set.getInt("target_user_id"),
                set.getInt("sender_user_id"),
                set.getString("sender_username"),
                set.getInt("room_id"),
                set.getString("room_name"),
                set.getString("message"),
                set.getInt("mention_type"),
                set.getInt("timestamp"),
                set.getInt("read") == 1,
                hasSenderFigure(set) ? set.getString("sender_figure") : ""
        );
    }

    public HabboMention(int targetUserId, int id, Habbo sender, Room room, String roomName, String message, int mentionType, int timestamp) {
        this(
                id,
                targetUserId,
                sender.getHabboInfo().getId(),
                sender.getHabboInfo().getUsername(),
                room.getId(),
                roomName,
                message,
                mentionType,
                timestamp,
                false,
                sender.getHabboInfo().getLook()
        );
    }

    private static boolean hasSenderFigure(ResultSet set) {
        try {
            set.findColumn("sender_figure");
            return true;
        } catch (SQLException e) {
            return false;
        }
    }
}
