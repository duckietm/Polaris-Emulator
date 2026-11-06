package polaris.hotel.modtool;

import java.sql.ResultSet;
import java.sql.SQLException;

public class CfhTopic {
    public final int id;
    public final String name;
    public final CfhActionType action;
    public final boolean ignoreTarget;
    public final String reply;
    public final ModToolPreset defaultSanction;

    public CfhTopic(ResultSet set, ModToolPreset defaultSanction) throws SQLException {
        this.id = set.getInt("id");
        this.name = set.getString("name_internal");
        this.action = CfhActionType.get(set.getString("action"));
        this.ignoreTarget = set.getString("ignore_target").equalsIgnoreCase("1");
        this.reply = set.getString("auto_reply");
        this.defaultSanction = defaultSanction;
    }
}
