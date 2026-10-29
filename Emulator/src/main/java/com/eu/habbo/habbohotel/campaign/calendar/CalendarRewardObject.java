package com.eu.habbo.habbohotel.campaign.calendar;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.users.subscriptions.SubscriptionHabboClub;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import java.sql.ResultSet;
import java.sql.SQLException;

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
        this.badge = set.getString("badge");
        this.itemId = set.getInt("item_id");
        this.subscription_type = set.getString("subscription_type");
        this.subscription_days = set.getInt("subscription_days");
        this.day = Math.max(0, set.getInt("day"));
    }

    public void give(Habbo habbo) {
        if (this.credits > 0) {
            habbo.giveCredits(this.credits);
        }

        if (this.pixels > 0) {
            habbo.givePixels(
                    (int) (this.pixels * (habbo.getHabboStats().hasActiveClub() ? CalendarManager.HC_MODIFIER : 1.0)));
        }

        if (this.points > 0) {
            habbo.givePoints(this.pointsType, this.points);
        }

        if (!this.badge.isEmpty()) {
            habbo.addBadge(this.badge);
        }

        if (this.subscription_type != null && !this.subscription_type.isEmpty()) {
            if ("HABBO_CLUB".equals(this.subscription_type)) {
                habbo.getHabboStats()
                        .createSubscription(SubscriptionHabboClub.HABBO_CLUB, this.subscription_days * 86400);
            } else {
                habbo.getHabboStats().createSubscription(this.subscription_type, this.subscription_days * 86400);
            }
        }

        if (this.itemId > 0) {
            Item item = getItem();

            if (item != null) {
                HabboItem habboItem = Emulator.getGameEnvironment()
                        .getItemManager()
                        .createItem(habbo.getHabboInfo().getId(), item, 0, 0, "");
                habbo.getInventory().getItemsComponent().addItem(habboItem);
                habbo.getClient().sendResponse(new AddHabboItemComposer(habboItem));
                habbo.getClient().sendResponse(new InventoryRefreshComposer());
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
        return Emulator.getGameEnvironment().getItemManager().getItem(this.itemId);
    }
}
