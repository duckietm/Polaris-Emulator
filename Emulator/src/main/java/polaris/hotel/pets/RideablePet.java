package polaris.hotel.pets;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.users.User;

public class RideablePet extends Pet {

    private User rider;
    private boolean hasSaddle;
    private boolean anyoneCanRide;
    private int saddleItemId;

    public RideablePet(ResultSet set) throws SQLException {
        super(set);
        this.rider = null;
    }

    public RideablePet(int type, int race, String color, String name, int userId) {
        super(type, race, color, name, userId);
        this.rider = null;
    }

    public boolean hasSaddle() {
        return this.hasSaddle;
    }

    public void hasSaddle(boolean hasSaddle) {
        this.hasSaddle = hasSaddle;
    }

    public boolean anyoneCanRide() {
        return this.anyoneCanRide;
    }

    public void setAnyoneCanRide(boolean anyoneCanRide) {
        this.anyoneCanRide = anyoneCanRide;
    }

    public User getRider() {
        return this.rider;
    }

    public void setRider(User rider) {
        this.rider = rider;
    }

    public int getSaddleItemId() {
        return saddleItemId;
    }

    public void setSaddleItemId(int saddleItemId) {
        this.saddleItemId = saddleItemId;
    }
}
