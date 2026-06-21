package com.eu.habbo.habbohotel.items;

public enum RedeemableSubscriptionType {
    HABBO_CLUB("hc"),
    BUILDERS_CLUB("bc");

    public final String subscriptionType;

    RedeemableSubscriptionType(String subscriptionType) {
        this.subscriptionType = subscriptionType;
    }

    public static RedeemableSubscriptionType fromString(String subscriptionType) {
        if (subscriptionType == null) return null;

        return switch (subscriptionType) {
            case "hc" -> HABBO_CLUB;
            case "bc" -> BUILDERS_CLUB;
            default -> null;
        };
    }
}
