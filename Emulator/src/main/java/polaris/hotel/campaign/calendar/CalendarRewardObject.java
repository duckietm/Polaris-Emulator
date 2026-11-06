package polaris.hotel.campaign.calendar;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.Emulator;
import polaris.hotel.items.Item;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.hotel.users.subscriptions.SubscriptionClub;
import polaris.messages.outgoing.inventory.AddUserItemComposer;
import polaris.messages.outgoing.inventory.InventoryRefreshComposer;

public class CalendarRewardObject {

    private final int id;
    private final String productName;
    private final String customImage;
    private final int credits;
    private final int pixels;
    private final int points;
    private final int pointsType;
    private final String badge;
    private final int itemId;
    private final String subscription_type;
    private final int subscription_days;
    /** The calendar day this reward belongs to, counted from 1; 0 (NULL in the database) for any day. */
    private final int day;

    public CalendarRewardObject(ResultSet set) throws SQLException {
        this.id = set.getInt("id");
        this.productName = set.getString("product_name");
        this.customImage = set.getString("custom_image");
        this.credits = set.getInt("credits");
        this.pixels = set.getInt("pixels");
        this.points = set.getInt("points");
        this.pointsType = set.getInt("points_type");
        // A reward without a badge holds NULL: give() and the product composer used to throw on it after the day
        // was already claimed.
        String badge = set.getString("badge");
        this.badge = badge == null ? "" : badge;
        this.itemId = set.getInt("item_id");
        this.subscription_type = set.getString("subscription_type");
        this.subscription_days = set.getInt("subscription_days");
        this.day = Math.max(0, set.getInt("day"));
    }

    public void give(User user) {
        if (this.credits > 0) {
            user.giveCredits(this.credits);
        }

        if (this.pixels > 0) {
            user.givePixels(
                    (int) (this.pixels * (user.getUserStats().hasActiveClub() ? CalendarManager.HC_MODIFIER : 1.0)));
        }

        if (this.points > 0) {
            user.givePoints(this.pointsType, this.points);
        }

        if (!this.badge.isEmpty()) {
            user.addBadge(this.badge);
        }

        if (this.subscription_type != null && !this.subscription_type.isEmpty()) {
            if ("HABBO_CLUB".equals(this.subscription_type)) {
                user.getUserStats().createSubscription(SubscriptionClub.CLUB, this.subscription_days * 86400);
            } else {
                user.getUserStats().createSubscription(this.subscription_type, this.subscription_days * 86400);
            }
        }

        if (this.itemId > 0) {
            Item item = getItem();

            if (item != null) {
                UserItem userItem = Emulator.getGameEnvironment()
                        .getItemManager()
                        .createItem(user.getUserInfo().getId(), item, 0, 0, "");
                if (userItem == null) return;
                user.getInventory().getItemsComponent().addItem(userItem);
                user.getClient().sendResponse(new AddUserItemComposer(userItem));
                user.getClient().sendResponse(new InventoryRefreshComposer());
            }
        }
    }

    public int getId() {
        return this.id;
    }

    /** The calendar day this reward belongs to, counted from 1; 0 when it can be given on any day. */
    public int getDay() {
        return this.day;
    }

    public String getCustomImage() {
        return this.customImage;
    }

    public int getCredits() {
        return this.credits;
    }

    public int getPixels() {
        return this.pixels;
    }

    public int getPoints() {
        return this.points;
    }

    public int getPointsType() {
        return this.pointsType;
    }

    public String getProductName() {
        return productName;
    }

    public String getSubscriptionType() {
        return subscription_type;
    }

    public int getSubscriptionDays() {
        return subscription_days;
    }

    public String getBadge() {
        return this.badge;
    }

    public Item getItem() {
        if (this.itemId <= 0) return null;
        return Emulator.getGameEnvironment().getItemManager().getItem(this.itemId);
    }
}
