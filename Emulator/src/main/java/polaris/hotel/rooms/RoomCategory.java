package polaris.hotel.rooms;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.navigation.ListMode;

@SuppressWarnings("NullableProblems")
public class RoomCategory implements Comparable<RoomCategory> {

    private int id;
    private int minRank;
    private String caption;
    private String captionSave;
    private boolean canTrade;
    private int maxUserCount;
    private boolean official;
    private ListMode displayMode;
    private int order;

    public RoomCategory(ResultSet set) throws SQLException {
        this.id = set.getInt("id");
        this.minRank = set.getInt("min_rank");
        this.caption = set.getString("caption");
        this.captionSave = set.getString("caption_save");
        this.canTrade = set.getBoolean("can_trade");
        this.maxUserCount = set.getInt("max_user_count");
        this.official = set.getString("public").equals("1");
        this.displayMode = ListMode.fromType(set.getInt("list_type"));
        this.order = set.getInt("order_num");
    }

    public int getId() {
        return this.id;
    }

    public int getMinRank() {
        return this.minRank;
    }

    public String getCaption() {
        return this.caption;
    }

    public String getCaptionSave() {
        return this.captionSave;
    }

    public boolean isCanTrade() {
        return this.canTrade;
    }

    public int getMaxUserCount() {
        return this.maxUserCount;
    }

    public boolean isPublic() {
        return this.official;
    }

    public ListMode getDisplayMode() {
        return this.displayMode;
    }

    public int getOrder() {
        return this.order;
    }

    @Override
    public int compareTo(RoomCategory o) {
        return o.getId() - this.getId();
    }
}
