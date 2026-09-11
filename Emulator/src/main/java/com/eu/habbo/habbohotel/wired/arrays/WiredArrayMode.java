package com.eu.habbo.habbohotel.wired.arrays;

public enum WiredArrayMode {
    LIST("list"),
    SLOTS("slots");

    private final String wireName;

    WiredArrayMode(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return this.wireName;
    }

    public static WiredArrayMode fromWireName(String value) {
        return SLOTS.wireName.equalsIgnoreCase(value) ? SLOTS : LIST;
    }
}
