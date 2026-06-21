package com.eu.habbo.habbohotel.wired;

public class WiredGiveRewardItem {
    private static final int MIN_PROBABILITY = 0;
    private static final int MAX_PROBABILITY = 100;

    public final int id;
    public final boolean badge;
    public final String data;
    public final int probability;

    public WiredGiveRewardItem(int id, boolean badge, String data, int probability) {
        this.id = id;
        this.badge = badge;
        this.data = data == null ? "" : data;
        this.probability = normalizeProbability(probability);
    }

    public WiredGiveRewardItem(String dataString) {
        String[] data = dataString == null ? new String[0] : dataString.split(",", -1);
        if (data.length < 4) {
            throw new IllegalArgumentException("Invalid wired reward item payload");
        }

        this.id = parseRequiredInteger(data[0], "id");
        this.badge = data[1].equalsIgnoreCase("0");
        this.data = data[2];
        this.probability = normalizeProbability(parseRequiredInteger(data[3], "probability"));
    }

    @Override
    public String toString() {
        return this.id + "," + (this.badge ? 0 : 1) + "," + this.data + "," + this.probability;
    }

    public String wiredString() {
        return (this.badge ? 0 : 1) + "," + this.data + "," + this.probability;
    }

    private static int parseRequiredInteger(String value, String field) {
        try {
            return Integer.parseInt(value == null ? "" : value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid wired reward item " + field, e);
        }
    }

    private static int normalizeProbability(int probability) {
        return Math.max(MIN_PROBABILITY, Math.min(MAX_PROBABILITY, probability));
    }
}
