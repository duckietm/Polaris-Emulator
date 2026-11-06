package polaris.hotel.catalog;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.Emulator;
import polaris.hotel.users.cache.UserOfferPurchase;
import polaris.messages.ServerMessage;

public class TargetOffer {
    public static volatile int ACTIVE_TARGET_OFFER_ID = 0;

    private final int id;
    private final int catalogItem;
    private final String identifier;
    private final int priceInCredits;
    private final int priceInActivityPoints;
    private final int activityPointsType;
    private final int purchaseLimit;
    private final int expirationTime;
    private final String title;
    private final String description;
    private final String imageUrl;
    private final String icon;
    private final String[] vars;

    public TargetOffer(ResultSet set) throws SQLException {
        this.id = set.getInt("id");
        this.identifier = set.getString("offer_code");
        this.priceInCredits = set.getInt("credits");
        this.priceInActivityPoints = set.getInt("points");
        this.activityPointsType = set.getInt("points_type");
        this.title = set.getString("title");
        this.description = set.getString("description");
        this.imageUrl = set.getString("image");
        this.icon = set.getString("icon");
        this.purchaseLimit = set.getInt("purchase_limit");
        this.expirationTime = set.getInt("end_timestamp");
        this.vars = set.getString("vars").split(";");
        this.catalogItem = set.getInt("catalog_item");
    }

    public void serialize(ServerMessage message, UserOfferPurchase purchase) {
        message.appendInt(purchase.getState());
        message.appendInt(this.id);
        message.appendString(this.identifier);
        message.appendString(this.identifier);
        message.appendInt(this.priceInCredits);
        message.appendInt(this.priceInActivityPoints);
        message.appendInt(this.activityPointsType);
        message.appendInt(Math.max(this.purchaseLimit - purchase.getAmount(), 0));
        message.appendInt(Math.max(this.expirationTime - Emulator.getIntUnixTimestamp(), 0));
        message.appendString(this.title);
        message.appendString(this.description);
        message.appendString(this.imageUrl);
        message.appendString(this.icon);
        message.appendInt(0);
        message.appendInt(this.vars.length);
        for (String variable : this.vars) {
            message.appendString(variable);
        }
    }

    public int getId() {
        return this.id;
    }

    public String getIdentifier() {
        return this.identifier;
    }

    public int getPriceInCredits() {
        return this.priceInCredits;
    }

    public int getPriceInActivityPoints() {
        return this.priceInActivityPoints;
    }

    public int getActivityPointsType() {
        return this.activityPointsType;
    }

    public int getPurchaseLimit() {
        return this.purchaseLimit;
    }

    public int getExpirationTime() {
        return this.expirationTime;
    }

    public String getTitle() {
        return this.title;
    }

    public String getDescription() {
        return this.description;
    }

    public String getImageUrl() {
        return this.imageUrl;
    }

    public String getIcon() {
        return this.icon;
    }

    public String[] getVars() {
        return this.vars;
    }

    public int getCatalogItem() {
        return this.catalogItem;
    }
}
