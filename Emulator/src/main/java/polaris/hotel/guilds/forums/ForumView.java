package polaris.hotel.guilds.forums;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ForumView {
    private final int userId;
    private final int guildId;
    private final int timestamp;

    public ForumView(int userId, int guildId, int timestamp) {
        this.userId = userId;
        this.guildId = guildId;
        this.timestamp = timestamp;
    }

    public ForumView(ResultSet set) throws SQLException {
        this.userId = set.getInt("user_id");
        this.guildId = set.getInt("guild_id");
        this.timestamp = set.getInt("timestamp");
    }

    public int getUserId() {
        return userId;
    }

    public int getGuildId() {
        return guildId;
    }

    public int getTimestamp() {
        return timestamp;
    }
}
