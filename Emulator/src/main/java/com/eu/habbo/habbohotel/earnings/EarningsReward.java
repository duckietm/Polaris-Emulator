package com.eu.habbo.habbohotel.earnings;

public class EarningsReward {
    public static final String TYPE_CREDITS = "credits";
    public static final String TYPE_PIXELS = "pixels";
    public static final String TYPE_POINTS = "points";
    public static final String TYPE_BADGE = "badge";
    public static final String TYPE_ITEM = "item";
    public static final String TYPE_HC_DAYS = "hc_days";

    private final String type;
    private final int amount;
    private final int pointsType;
    private final String data;

    public EarningsReward(String type, int amount, int pointsType) {
        this(type, amount, pointsType, "");
    }

    public EarningsReward(String type, int amount, int pointsType, String data) {
        this.type = type;
        this.amount = Math.max(0, amount);
        this.pointsType = Math.max(0, pointsType);
        this.data = data == null ? "" : data;
    }

    public String getType() {
        return type;
    }

    public int getAmount() {
        return amount;
    }

    public int getPointsType() {
        return pointsType;
    }

    public String getData() {
        return data;
    }
}
