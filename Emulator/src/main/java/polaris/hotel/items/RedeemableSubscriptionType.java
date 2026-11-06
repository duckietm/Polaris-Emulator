package polaris.hotel.items;

public enum RedeemableSubscriptionType {
    CLUB("hc"),
    BUILDERS_CLUB("bc");

    public final String subscriptionType;

    RedeemableSubscriptionType(String subscriptionType) {
        this.subscriptionType = subscriptionType;
    }

    public static RedeemableSubscriptionType fromString(String subscriptionType) {
        if (subscriptionType == null) return null;

        switch (subscriptionType) {
            case "hc":
                return CLUB;
            case "bc":
                return BUILDERS_CLUB;
        }

        return null;
    }
}
