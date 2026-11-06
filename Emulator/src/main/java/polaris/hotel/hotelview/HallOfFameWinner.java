package polaris.hotel.hotelview;

import java.sql.ResultSet;
import java.sql.SQLException;

public class HallOfFameWinner implements Comparable<HallOfFameWinner> {

    private int id;

    private String username;

    private String look;

    private int points;

    public HallOfFameWinner(ResultSet set) throws SQLException {
        this.id = set.getInt("id");
        this.username = set.getString("username");
        this.look = set.getString("look");
        this.points = set.getInt("hof_points");
    }

    public int getId() {
        return this.id;
    }

    public String getUsername() {
        return this.username;
    }

    public String getLook() {
        return this.look;
    }

    public int getPoints() {
        return this.points;
    }

    @Override
    public int compareTo(HallOfFameWinner o) {
        return o.getPoints() - this.points;
    }
}
