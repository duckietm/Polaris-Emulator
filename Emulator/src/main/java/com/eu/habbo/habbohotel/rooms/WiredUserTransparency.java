package com.eu.habbo.habbohotel.rooms;

/**
 * How transparent the official set furni transparency action (61) makes an avatar: 0 fully visible,
 * 100 invisible, whether clicks go through it, and the easing of the change.
 */
public record WiredUserTransparency(int transparency, boolean clickThrough, int easing) {
    public static final WiredUserTransparency NONE = new WiredUserTransparency(0, false, 0);

    public WiredUserTransparency {
        transparency = Math.max(0, Math.min(100, transparency));
        easing = Math.max(0, Math.min(4, easing));
    }

    /** Fully visible and clickable: the avatar as it is without the action. */
    public boolean isDefault() {
        return this.transparency == 0 && !this.clickThrough;
    }
}
