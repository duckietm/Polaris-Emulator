package polaris.hotel.catalog;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ClothItem {

    public int id;

    public String name;

    public int[] setId;

    public ClothItem(ResultSet set) throws SQLException {
        this.id = set.getInt("id");
        this.name = set.getString("name");
        String[] parts = set.getString("setid").split(",");

        this.setId = new int[parts.length];
        for (int i = 0; i < this.setId.length; i++) {
            this.setId[i] = Integer.parseInt(parts[i]);
        }
    }
}
