package polaris.hotel.pets;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class HorsePet extends RideablePet {
    private int hairColor;
    private int hairStyle;

    public HorsePet(ResultSet set) throws SQLException {
        super(set);
        this.hairColor = set.getInt("hair_color");
        this.hairStyle = set.getInt("hair_style");
        this.hasSaddle(set.getString("saddle").equalsIgnoreCase("1"));
        this.setAnyoneCanRide(set.getString("ride").equalsIgnoreCase("1"));
        this.setSaddleItemId(set.getInt("saddle_item_id"));
    }

    public HorsePet(int type, int race, String color, String name, int userId) {
        super(type, race, color, name, userId);
        this.hairColor = 0;
        this.hairStyle = -1;
        this.hasSaddle(false);
        this.setAnyoneCanRide(false);
    }

    /**
     * Writes the horse columns and then the base pet row on the same connection. A failure in
     * either throws before {@link Pet#save(Connection)} clears {@code needsUpdate}, so the next
     * save retries both instead of losing the horse fields.
     */
    @Override
    public void save(Connection connection) throws SQLException {
        if (!this.needsUpdate) return;
        if (super.getId() > 0) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE users_pets SET hair_style = ?, hair_color = ?, saddle = ?, ride = ?, saddle_item_id = ? WHERE id = ?")) {
                statement.setInt(1, this.hairStyle);
                statement.setInt(2, this.hairColor);
                statement.setString(3, this.hasSaddle() ? "1" : "0");
                statement.setString(4, this.anyoneCanRide() ? "1" : "0");
                statement.setInt(5, this.getSaddleItemId());
                statement.setInt(6, super.getId());
                statement.executeUpdate();
            }
        }
        super.save(connection);
    }

    public int getHairColor() {
        return this.hairColor;
    }

    public void setHairColor(int hairColor) {
        this.hairColor = hairColor;
    }

    public int getHairStyle() {
        return this.hairStyle;
    }

    public void setHairStyle(int hairStyle) {
        this.hairStyle = hairStyle;
    }
}
