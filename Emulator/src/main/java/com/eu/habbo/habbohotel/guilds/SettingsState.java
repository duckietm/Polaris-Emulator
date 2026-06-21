package com.eu.habbo.habbohotel.guilds;

public enum SettingsState {
    EVERYONE(0),
    MEMBERS(1),
    ADMINS(2),
    OWNER(3);

    public final int state;

    SettingsState(int state) {
        this.state = state;
    }

    public static SettingsState fromValue(int state) {
        return switch (state) {
            case 0 -> EVERYONE;
            case 1 -> MEMBERS;
            case 2 -> ADMINS;
            case 3 -> OWNER;
            default -> EVERYONE;
        };
    }
}