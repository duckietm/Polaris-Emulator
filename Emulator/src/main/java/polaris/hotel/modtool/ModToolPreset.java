package polaris.hotel.modtool;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ModToolPreset {
    public final int id;
    public final String name;
    public final String message;
    public final String reminder;
    public final int banLength;
    public final int muteLength;

    public ModToolPreset(ResultSet set) throws SQLException {
        this.id = set.getInt("id");
        this.name = set.getString("name");
        this.message = set.getString("message");
        this.reminder = set.getString("reminder");
        this.banLength = set.getInt("ban_for");
        this.muteLength = set.getInt("mute_for");
    }
}
