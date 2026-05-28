package com.eu.habbo.habbohotel.wheel;

// A row in the "latest winners" panel. Denormalized (username/look stored at win time).
public class WheelRecentWin {
    public final String username;
    public final String look;
    public final String prizeLabel;

    public WheelRecentWin(String username, String look, String prizeLabel) {
        this.username = username != null ? username : "";
        this.look = look != null ? look : "";
        this.prizeLabel = prizeLabel != null ? prizeLabel : "";
    }
}
