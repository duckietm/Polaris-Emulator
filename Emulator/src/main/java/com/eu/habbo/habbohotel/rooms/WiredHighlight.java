package com.eu.habbo.habbohotel.rooms;

/**
 * One official wired highlight (AIR set furni highlight, action 62): a style, a strength, a colour and
 * how it animates. The editor rules are applied here, so a stored, a loaded and a sent highlight agree:
 * darken has no colour, brighten is always white and pulsing. A strength of 0 means no highlight.
 */
public record WiredHighlight(int type, int strength, int color, int animation, int easing) {
    public static final int TYPE_OUTLINE = 0;
    public static final int TYPE_SOFT_GLOW = 1;
    public static final int TYPE_DARKEN = 2;
    public static final int TYPE_COLOR_TINT = 3;
    public static final int TYPE_BRIGHTEN = 4;
    public static final int TYPE_NEON = 5;
    public static final int MAXIMUM_STRENGTH = 100;
    public static final int MAXIMUM_EASING = 4;
    public static final int ANIMATION_NONE = 0;
    public static final int ANIMATION_PULSE = 1;
    public static final int WHITE = 0xFFFFFF;

    /** No highlight: what a client gets for an item or user whose highlight was cleared. */
    public static final WiredHighlight NONE = new WiredHighlight(TYPE_OUTLINE, 0, 0, ANIMATION_NONE, 0);

    public WiredHighlight {
        type = clamp(type, TYPE_OUTLINE, TYPE_NEON);
        strength = clamp(strength, 0, MAXIMUM_STRENGTH);
        color = clamp(color, 0, WHITE);
        animation = clamp(animation, ANIMATION_NONE, ANIMATION_PULSE);
        easing = clamp(easing, 0, MAXIMUM_EASING);
        if (type == TYPE_DARKEN) {
            color = 0;
        } else if (type == TYPE_BRIGHTEN) {
            color = WHITE;
            animation = ANIMATION_PULSE;
        }
    }

    public boolean isCleared() {
        return this.strength == 0;
    }

    /**
     * The strength the client is sent. The official editor saves half the brighten strength (0 or 38 for
     * its 0 or 76), and the renderer expects the whole of it back.
     */
    public int wireStrength() {
        return this.type == TYPE_BRIGHTEN ? Math.min(MAXIMUM_STRENGTH, this.strength * 2) : this.strength;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
