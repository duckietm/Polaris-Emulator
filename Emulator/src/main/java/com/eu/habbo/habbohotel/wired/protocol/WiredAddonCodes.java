package com.eu.habbo.habbohotel.wired.protocol;

import java.util.Map;

/**
 * Official addon and variable codes (AIR {@code AddonCodes} and {@code VariableCodes}, build
 * WIN63-202610131318). Our extra boxes write codes from the action space; the addon and variable
 * definition packets carry the official code instead. Official addon codes run 0..22, 1000..1005,
 * 1200..1205 and 2000..2003; our own addons start at 3000.
 */
public final class WiredAddonCodes {
    public static final int FIRST_CUSTOM_ADDON = 3000;

    private static final Map<Integer, Integer> ADDONS = Map.ofEntries(
            Map.entry(66, 0), // condition evaluation
            Map.entry(63, 1), // random
            Map.entry(62, 2), // unseen
            Map.entry(65, 5), // execution limit
            Map.entry(59, 6), // no move animation
            Map.entry(61, 7), // move physics
            Map.entry(58, 8), // carry users
            Map.entry(60, 9), // animation time
            Map.entry(56, 10), // furni selector filter
            Map.entry(57, 11), // user selector filter
            Map.entry(78, 12), // furni variable filter
            Map.entry(77, 13), // user variable filter
            Map.entry(67, 14), // username placeholder
            Map.entry(80, 15), // variable placeholder
            Map.entry(85, 16), // variable capturer
            Map.entry(64, 17), // execute in order
            Map.entry(68, 19), // furni name placeholder
            Map.entry(113, 20), // custom contract
            Map.entry(136, 21), // projectile
            Map.entry(97, 22), // jump strength (movement curve)
            Map.entry(79, 1000), // variable text converter
            Map.entry(82, 1001), // variable level up
            Map.entry(98, 1002), // variable time util
            Map.entry(1003, 1003), // variable add-on share
            Map.entry(1004, 1004), // variable add-on import
            Map.entry(1005, 1005), // variable leaderboard
            Map.entry(130, 1200), // variable fx: health points
            Map.entry(131, 1201), // progress bar
            Map.entry(132, 1202), // levelling progress
            Map.entry(133, 1203), // status bar
            Map.entry(134, 1204), // boss bar
            Map.entry(135, 1205), // number display
            Map.entry(2000, 2000), // global placeholder
            Map.entry(151, 2001), // achievement enabler
            Map.entry(128, 2002), // variables web api
            Map.entry(2003, 2003), // link speaker
            // Ours.
            Map.entry(0, 3000), // wf_label
            Map.entry(100, 3002), // currency chest
            Map.entry(101, 3003), // furni chest
            Map.entry(110, 3004), // contract payment
            Map.entry(111, 3005), // contract reward
            Map.entry(112, 3006), // contract trade
            Map.entry(127, 3007)); // array capture variable

    private static final Map<Integer, Integer> VARIABLES = Map.of(
            71, 0, // furni
            70, 1, // user
            72, 2, // global (room)
            84, 3, // context
            81, 4, // reference
            108, 5, // quest
            109, 6, // quest chain
            83, 7, // echo
            2008, 8, // daily task
            2009, 9); // pointer

    private WiredAddonCodes() {}

    /** The official code of an addon box writing {@code legacyCode}, or -1 when there is none. */
    public static int officialAddon(int legacyCode) {
        return ADDONS.getOrDefault(legacyCode, -1);
    }

    /** The official code of a variable box writing {@code legacyCode}, or -1 when there is none. */
    public static int officialVariable(int legacyCode) {
        return VARIABLES.getOrDefault(legacyCode, -1);
    }
}
