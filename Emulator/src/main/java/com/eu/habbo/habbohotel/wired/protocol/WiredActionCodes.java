package com.eu.habbo.habbohotel.wired.protocol;

import java.util.Map;

/**
 * Official action codes (AIR {@code ActionTypeCodes}, build WIN63-202610131318). Our effect boxes write
 * their own code space; the action definition packet carries the official code instead. Actions the
 * official client lacks go out as 1000 plus their own code, so they stay traceable. Boxes that share an
 * editor share a code, as before.
 */
public final class WiredActionCodes {
    public static final int FIRST_CUSTOM_ACTION = 1000;

    private static final Map<Integer, Integer> OFFICIAL = Map.ofEntries(
            Map.entry(0, 0), // toggle furni state
            Map.entry(1, 1), // reset
            Map.entry(3, 3), // match to snapshot
            Map.entry(4, 4), // move/rotate furni
            Map.entry(6, 6), // give score
            Map.entry(7, 7), // show message
            Map.entry(8, 8), // teleport
            Map.entry(9, 9), // join team
            Map.entry(10, 10), // leave team
            Map.entry(11, 11), // chase
            Map.entry(12, 12), // flee
            Map.entry(13, 13), // move to direction
            Map.entry(14, 14), // give score to team
            Map.entry(15, 15), // toggle to random state
            Map.entry(16, 16), // move furni to
            Map.entry(17, 17), // give reward
            Map.entry(18, 18), // call another stack
            Map.entry(19, 19), // kick from room
            Map.entry(20, 20), // mute user
            Map.entry(21, 21), // bot teleport
            Map.entry(22, 22), // bot move
            Map.entry(23, 23), // bot talk
            Map.entry(24, 24), // bot give hand item
            Map.entry(25, 25), // bot follow avatar
            Map.entry(26, 26), // bot change figure
            Map.entry(27, 27), // bot talk to avatar
            Map.entry(41, 28), // control clock
            Map.entry(39, 29), // set furni altitude
            Map.entry(33, 30), // send signal
            Map.entry(34, 31), // freeze user
            Map.entry(35, 32), // unfreeze user
            Map.entry(40, 33), // relative furni move
            Map.entry(38, 34), // move furni to furni
            Map.entry(36, 35), // move furni to user
            Map.entry(86, 36), // negative call another stack
            Map.entry(87, 37), // negative send signal
            Map.entry(42, 38), // adjust clock
            Map.entry(69, 39), // give variable
            Map.entry(73, 40), // remove variable
            Map.entry(74, 41), // change variable
            Map.entry(43, 42), // move user
            Map.entry(37, 43), // move user to furni
            Map.entry(138, 44), // teleport to room
            Map.entry(99, 45), // give currency from chest
            Map.entry(102, 46), // give furni from chest
            Map.entry(104, 47), // initiate transaction
            Map.entry(105, 48), // cancel transaction
            Map.entry(137, 49), // write to log (negative 50 shares it here)
            Map.entry(150, 51), // progress achievement
            Map.entry(121, 52), // give effect (give hand item shares its editor)
            Map.entry(125, 53), // override height
            Map.entry(129, 54), // click settings
            Map.entry(106, 55), // place furni
            Map.entry(107, 56), // remove furni
            Map.entry(95, 57), // move as group
            Map.entry(152, 58), // progress reward track
            Map.entry(153, 59), // reset reward track
            Map.entry(114, 61), // set furni transparency
            Map.entry(158, 62)); // set furni highlight

    private WiredActionCodes() {}

    /** The code an action box writing {@code legacyCode} goes out with. */
    public static int official(int legacyCode) {
        Integer official = OFFICIAL.get(legacyCode);
        return official != null ? official : FIRST_CUSTOM_ACTION + legacyCode;
    }
}
