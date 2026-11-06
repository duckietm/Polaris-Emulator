package polaris.hotel.mentions;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;

public class UserMention {

    public static final int TYPE_DIRECT = 0;
    public static final int TYPE_ROOM = 1;

    private final int id;
    private final int targetUserId;
    private final int senderUserId;
    private final String senderUsername;
    private final int roomId;
    private final String roomName;
    private final String message;
    private final int mentionType;
    private final int timestamp;
    private final boolean read;
    private final String senderFigure;

    public UserMention(ResultSet set) throws SQLException {
        this.id = set.getInt("id");
        this.targetUserId = set.getInt("target_user_id");
        this.senderUserId = set.getInt("sender_user_id");
        this.senderUsername = set.getString("sender_username");
        this.roomId = set.getInt("room_id");
        this.roomName = set.getString("room_name");
        this.message = set.getString("message");
        this.mentionType = set.getInt("mention_type");
        this.timestamp = set.getInt("timestamp");
        this.read = set.getInt("read") == 1;
        this.senderFigure = hasSenderFigure(set) ? set.getString("sender_figure") : "";
    }

    private static boolean hasSenderFigure(ResultSet set) {
        try {
            set.findColumn("sender_figure");
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public UserMention(
            int targetUserId,
            int id,
            User sender,
            Room room,
            String roomName,
            String message,
            int mentionType,
            int timestamp) {
        this.id = id;
        this.targetUserId = targetUserId;
        this.senderUserId = sender.getUserInfo().getId();
        this.senderUsername = sender.getUserInfo().getUsername();
        this.roomId = room.getId();
        this.roomName = roomName;
        this.message = message;
        this.mentionType = mentionType;
        this.timestamp = timestamp;
        this.read = false;
        this.senderFigure = sender.getUserInfo().getLook();
    }

    public int getId() {
        return this.id;
    }

    public int getTargetUserId() {
        return this.targetUserId;
    }

    public int getSenderUserId() {
        return this.senderUserId;
    }

    public String getSenderUsername() {
        return this.senderUsername;
    }

    public int getRoomId() {
        return this.roomId;
    }

    public String getRoomName() {
        return this.roomName;
    }

    public String getMessage() {
        return this.message;
    }

    public int getMentionType() {
        return this.mentionType;
    }

    public int getTimestamp() {
        return this.timestamp;
    }

    public boolean isRead() {
        return this.read;
    }

    public String getSenderFigure() {
        return this.senderFigure == null ? "" : this.senderFigure;
    }
}
