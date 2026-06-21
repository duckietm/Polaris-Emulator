package com.eu.habbo.habbohotel.earnings;

public record EarningsReward(String type, int amount, int pointsType, String data) {
    public static final String TYPE_CREDITS = "credits";
    public static final String TYPE_PIXELS = "pixels";
    public static final String TYPE_POINTS = "points";
    public static final String TYPE_BADGE = "badge";
    public static final String TYPE_ITEM = "item";
    public static final String TYPE_HC_DAYS = "hc_days";

    public EarningsReward {
        amount = Math.max(0, amount);
        pointsType = Math.max(0, pointsType);
        data = data == null ? "" : data;
    }

    public EarningsReward(String type, int amount, int pointsType) {
        this(type, amount, pointsType, "");
    }
}
