package polaris.hotel.users.cache;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.users.User;

public class UserOfferPurchase {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserOfferPurchase.class);
    private final int offerId;
    private int state;
    private int amount;
    private int lastPurchaseTimestamp;
    private boolean needsUpdate = false;

    public UserOfferPurchase(ResultSet set) throws SQLException {
        this.offerId = set.getInt("offer_id");
        this.state = set.getInt("state");
        this.amount = set.getInt("amount");
        this.lastPurchaseTimestamp = set.getInt("last_purchase");
    }

    private UserOfferPurchase(int userId, int offerId) {
        this.offerId = offerId;
    }

    public static UserOfferPurchase getOrCreate(User user, int offerId) {
        UserOfferPurchase purchase = user.getUserStats().getUserOfferPurchase(offerId);

        if (purchase == null) {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO users_target_offer_purchases (user_id, offer_id) VALUES (?, ?)")) {
                statement.setInt(1, user.getUserInfo().getId());
                statement.setInt(2, offerId);
                statement.execute();
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
                return null;
            }

            purchase = new UserOfferPurchase(user.getUserInfo().getId(), offerId);
            user.getUserStats().addUserOfferPurchase(purchase);
        }

        return purchase;
    }

    public int getOfferId() {
        return this.offerId;
    }

    public int getState() {
        return this.state;
    }

    public void setState(int state) {
        this.state = state;
        this.needsUpdate = true;
    }

    public int getAmount() {
        return this.amount;
    }

    public void incrementAmount(int amount) {
        this.amount += amount;
        this.needsUpdate = true;
    }

    public int getLastPurchaseTimestamp() {
        return this.lastPurchaseTimestamp;
    }

    public void setLastPurchaseTimestamp(int timestamp) {
        this.lastPurchaseTimestamp = timestamp;
        this.needsUpdate = true;
    }

    public void update(int amount, int timestamp) {
        this.amount += amount;
        this.lastPurchaseTimestamp = timestamp;
        this.needsUpdate = true;
    }

    public boolean needsUpdate() {
        return this.needsUpdate;
    }
}
