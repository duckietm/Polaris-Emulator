package com.eu.habbo.habbohotel.items.interactions.mysterybox;

import java.util.Locale;
import java.util.Set;

/**
 * The colour a box or a key is: the last word of the furniture name when it is one of the eight the
 * client knows how to draw, and nothing otherwise. A hotel with a single pair of furniture therefore
 * works without naming colours at all, because two colourless pieces match each other.
 */
public final class MysteryBoxColour {
    static final Set<String> KNOWN = Set.of("purple", "blue", "green", "yellow", "lilac", "orange", "turquoise", "red");

    private MysteryBoxColour() {}

    public static String of(String itemName) {
        if (itemName == null || itemName.isEmpty()) {
            return "";
        }

        String name = itemName.toLowerCase(Locale.ROOT);
        int separator = name.lastIndexOf('_');
        String last = separator < 0 ? name : name.substring(separator + 1);

        return KNOWN.contains(last) ? last : "";
    }
}
