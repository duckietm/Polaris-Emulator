package polaris.hotel.users.custombadge;

public class CustomBadgeSettings {

    private final String badgePath;
    private final String badgeUrl;
    private final int priceBadge;
    private final int currencyType;

    public CustomBadgeSettings(String badgePath, String badgeUrl, int priceBadge, int currencyType) {
        this.badgePath = badgePath;
        this.badgeUrl = badgeUrl;
        this.priceBadge = priceBadge;
        this.currencyType = currencyType;
    }

    public String getBadgePath() {
        return this.badgePath;
    }

    public String getBadgeUrl() {
        return this.badgeUrl;
    }

    public int getPriceBadge() {
        return this.priceBadge;
    }

    public int getCurrencyType() {
        return this.currencyType;
    }
}
