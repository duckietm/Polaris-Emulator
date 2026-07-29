package com.eu.habbo.habbohotel.wired.arrays;

public enum WiredArrayFormat {
    SIMPLE("simple"),
    RECORD("record");

    private final String wireName;

    WiredArrayFormat(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return this.wireName;
    }

    public static WiredArrayFormat fromWireName(String value) {
        return RECORD.wireName.equalsIgnoreCase(value) ? RECORD : SIMPLE;
    }
}
