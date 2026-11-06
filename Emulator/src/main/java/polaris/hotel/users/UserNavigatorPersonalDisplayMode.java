package polaris.hotel.users;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.navigation.DisplayMode;
import polaris.hotel.navigation.ListMode;

public class UserNavigatorPersonalDisplayMode {
    public ListMode listMode;
    public DisplayMode displayMode;

    public UserNavigatorPersonalDisplayMode(ListMode listMode, DisplayMode collapsed) {
        this.listMode = listMode;
        this.displayMode = collapsed;
    }

    public UserNavigatorPersonalDisplayMode(ResultSet set) throws SQLException {
        this.listMode = set.getString("list_type").equals("thumbnails") ? ListMode.THUMBNAILS : ListMode.LIST;
        this.displayMode = DisplayMode.valueOf(set.getString("display").toUpperCase());
    }
}
