package com.eu.habbo.habbohotel.catalog;

import com.eu.habbo.Emulator;
import com.eu.habbo.WiredPlatform;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.messages.ISerialize;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.TimeZone;

public class ClubOffer implements ISerialize {
    /** AIR's club_buy page: the Habbo Club and VIP offers. */
    public static final int WINDOW_CLUB_BUY = 0;
    /** AIR's vip_buy page (the window this server always served). */
    public static final int WINDOW_HABBO_CLUB = 1;

    public static final int WINDOW_BUILDERS_CLUB = 2;
    public static final int WINDOW_BUILDERS_CLUB_ADDONS = 3;
    /** AIR's loyalty_vip_buy page: the club offers paid in points only. */
    public static final int WINDOW_LOYALTY_VIP = 6;
    /** The settings key narrowing the loyalty window to one points type; below 0 any points type counts. */
    public static final String LOYALTY_POINTS_TYPE_KEY = "catalog.club.loyalty.points_type";

    public enum OfferType {
        HC,
        VIP,
        BUILDERS_CLUB,
        BUILDERS_CLUB_ADDON;

        public static OfferType fromDatabase(String value) {
            if (value == null) {
                return HC;
            }

            for (OfferType type : OfferType.values()) {
                if (type.name().equalsIgnoreCase(value)) {
                    return type;
                }
            }

            return HC;
        }
    }

    private final int id;

    private final String name;

    private final int days;

    private final int credits;

    private final int points;

    private final int pointsType;

    private final OfferType type;

    private final boolean vip;

    private final boolean deal;

    private final boolean giftable;

    public ClubOffer(ResultSet set) throws SQLException {
        this.id = set.getInt("id");
        this.name = set.getString("name");
        this.days = set.getInt("days");
        this.credits = set.getInt("credits");
        this.points = set.getInt("points");
        this.pointsType = set.getInt("points_type");
        this.type = OfferType.fromDatabase(set.getString("type"));
        this.vip = this.type == OfferType.VIP;
        this.deal = set.getString("deal").equals("1");
        boolean giftable = false;
        try {
            giftable = "1".equals(set.getString("giftable"));
        } catch (SQLException ignored) {
        }
        this.giftable = giftable;
    }

    public int getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public int getDays() {
        return this.days;
    }

    public int getCredits() {
        return this.credits;
    }

    public int getPoints() {
        return this.points;
    }

    public int getPointsType() {
        return this.pointsType;
    }

    public OfferType getType() {
        return this.type;
    }

    public boolean isVip() {
        return this.vip;
    }

    public boolean isDeal() {
        return this.deal;
    }

    public boolean isGiftable() {
        return this.giftable;
    }

    public boolean isBuildersClubSubscription() {
        return this.type == OfferType.BUILDERS_CLUB;
    }

    public boolean isBuildersClubAddon() {
        return this.type == OfferType.BUILDERS_CLUB_ADDON;
    }

    public boolean isHabboClubOffer() {
        return this.type == OfferType.HC || this.type == OfferType.VIP;
    }

    public boolean isSubscriptionOffer() {
        return !this.isBuildersClubAddon();
    }

    public int getWindowId() {
        if (this.isBuildersClubAddon()) {
            return WINDOW_BUILDERS_CLUB_ADDONS;
        }

        if (this.isBuildersClubSubscription()) {
            return WINDOW_BUILDERS_CLUB;
        }

        return WINDOW_HABBO_CLUB;
    }

    public boolean belongsToWindow(int windowId) {
        ConfigurationManager config = windowId == WINDOW_LOYALTY_VIP ? WiredPlatform.configuration() : null;
        int loyaltyPointsType = config != null ? config.getInt(LOYALTY_POINTS_TYPE_KEY, -1) : -1;

        return this.belongsToWindow(windowId, loyaltyPointsType);
    }

    /**
     * The offer window AIR asks for: 0 (club_buy) and 1 (vip_buy) hold the Habbo Club and VIP offers, 6
     * (loyalty_vip_buy) the ones of those paid in points only (of {@code loyaltyPointsType} when it is 0 or more),
     * 2 and 3 the Builders Club membership and add-ons.
     */
    public boolean belongsToWindow(int windowId, int loyaltyPointsType) {
        if (windowId == WINDOW_CLUB_BUY) {
            return this.isHabboClubOffer();
        }

        if (windowId == WINDOW_LOYALTY_VIP) {
            return this.isHabboClubOffer()
                    && this.credits == 0
                    && this.points > 0
                    && (loyaltyPointsType < 0 || this.pointsType == loyaltyPointsType);
        }

        return this.getWindowId() == windowId;
    }

    @Override
    public void serialize(ServerMessage message) {
        serialize(message, Emulator.getIntUnixTimestamp());
    }

    public void serialize(ServerMessage message, int expireTimestamp) {
        expireTimestamp = Math.max(Emulator.getIntUnixTimestamp(), expireTimestamp);
        message.appendInt(this.id);
        message.appendString(this.name);
        message.appendBoolean(false); // unused
        message.appendInt(this.credits);
        message.appendInt(this.points);
        message.appendInt(this.pointsType);
        message.appendBoolean(this.vip);

        long seconds = this.days * 86400L;

        long secondsTotal = seconds;

        // The offer only carries months and days, so a year counts as 12 months: a 600-day
        // Builders Club add-on must reach the client as 19 months and 11 days (+600 furni).
        int totalMonths = (int) Math.floor(seconds / (86400.0 * 31));
        seconds -= totalMonths * (86400 * 31);

        int totalDays = (int) Math.floor(seconds / 86400.0);
        seconds -= totalDays * 86400L;

        message.appendInt(totalMonths);
        message.appendInt(totalDays);
        message.appendBoolean(this.giftable);
        message.appendInt(totalDays);

        long expiresAt = expireTimestamp;

        if (this.isSubscriptionOffer()) {
            expiresAt += secondsTotal;
        }

        Calendar cal = Calendar.getInstance();
        cal.setTimeZone(TimeZone.getTimeZone("UTC"));
        cal.setTimeInMillis(expiresAt * 1000L);
        message.appendInt(cal.get(Calendar.YEAR));
        message.appendInt(cal.get(Calendar.MONTH) + 1);
        message.appendInt(cal.get(Calendar.DAY_OF_MONTH));
    }
}
