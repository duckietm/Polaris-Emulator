package com.eu.habbo.habbohotel.users;

/**
 * Server-side model of the official {@code DiscordPreferences} value object
 * (packet 1600 / 2774). Version 0 is the "never saved" state the client uses to decide whether
 * to offer the settings popup.
 */
public class DiscordPreferences {
    public static final DiscordPreferences UNINITIALIZED = new DiscordPreferences(0, false, false, false, false);

    private final int version;
    private final boolean showHabbo;
    private final boolean shareActivity;
    private final boolean hideInHiddenRooms;
    private final boolean allowJoining;

    public DiscordPreferences(
            int version, boolean showHabbo, boolean shareActivity, boolean hideInHiddenRooms, boolean allowJoining) {
        this.version = Math.max(0, version);
        this.showHabbo = showHabbo;
        this.shareActivity = shareActivity;
        this.hideInHiddenRooms = hideInHiddenRooms;
        this.allowJoining = allowJoining;
    }

    public int getVersion() {
        return this.version;
    }

    public boolean isShowHabbo() {
        return this.showHabbo;
    }

    public boolean isShareActivity() {
        return this.shareActivity;
    }

    public boolean isHideInHiddenRooms() {
        return this.hideInHiddenRooms;
    }

    public boolean isAllowJoining() {
        return this.allowJoining;
    }
}
