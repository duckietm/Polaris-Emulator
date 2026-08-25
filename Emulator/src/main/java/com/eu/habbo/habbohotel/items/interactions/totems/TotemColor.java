package com.eu.habbo.habbohotel.items.interactions.totems;

public enum TotemColor {

    NONE(0),
    RED(1),
    YELLOW(2),
    BLUE(3);

    public final int color;

    TotemColor(int color) {
        this.color = color;
    }

    public static TotemColor fromInt(int color) {
        for(TotemColor totemColor : TotemColor.values()) {
            if(totemColor.color == color)
                return totemColor;
        }

        return NONE;
    }
}
