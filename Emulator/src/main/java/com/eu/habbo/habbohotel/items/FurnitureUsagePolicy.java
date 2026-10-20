package com.eu.habbo.habbohotel.items;

import com.eu.habbo.habbohotel.items.interactions.InteractionInformationTerminal;
import com.eu.habbo.habbohotel.items.interactions.InteractionPostIt;
import com.eu.habbo.habbohotel.items.interactions.InteractionPuzzleBox;
import com.eu.habbo.habbohotel.items.interactions.InteractionRandomState;
import com.eu.habbo.habbohotel.items.interactions.InteractionSwitch;
import com.eu.habbo.habbohotel.items.interactions.InteractionSwitchRemoteControl;
import com.eu.habbo.habbohotel.items.interactions.InteractionTeleport;
import com.eu.habbo.habbohotel.items.interactions.InteractionVendingMachine;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredRoomLinker;
import com.eu.habbo.habbohotel.users.HabboItem;

/**
 * The usage policy a furni is sent with (AIR {@code RoomWidgetFurniInfoUsagePolicyEnum}): who may use
 * it. The floor and wall item packets and the server's own playtest check read it from here, so the
 * client and the server agree on which furni everybody may use.
 */
public final class FurnitureUsagePolicy {
    /** Nobody uses it (a furni with one state). */
    public static final int NOBODY = 0;
    /** Only users with rights in the room use it. */
    public static final int RIGHTS = 1;
    /** Everybody in the room uses it. */
    public static final int EVERYBODY = 2;

    private FurnitureUsagePolicy() {}

    public static int of(HabboItem item) {
        if (item == null) {
            return NOBODY;
        }

        if (isUsableByEverybody(item)) {
            return EVERYBODY;
        }

        return item.isUsable() ? RIGHTS : NOBODY;
    }

    /**
     * Furni that anyone in the room may use: teleporters (not the wired room linker), switches,
     * remote switches, vending machines, information terminals, post-its, puzzle boxes and random
     * state furni. The same on the floor and on the wall.
     */
    public static boolean isUsableByEverybody(HabboItem item) {
        return (item instanceof InteractionTeleport && !(item instanceof InteractionWiredRoomLinker))
                || item instanceof InteractionSwitch
                || item instanceof InteractionSwitchRemoteControl
                || item instanceof InteractionVendingMachine
                || item instanceof InteractionInformationTerminal
                || item instanceof InteractionPostIt
                || item instanceof InteractionPuzzleBox
                || item instanceof InteractionRandomState;
    }
}
