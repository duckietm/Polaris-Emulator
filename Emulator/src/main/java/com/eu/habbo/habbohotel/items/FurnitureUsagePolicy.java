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
import java.util.Locale;
import java.util.Set;

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

    /** Game furni AIR shows without a Use button. */
    static final Set<String> NO_USE_BUTTON = Set.of(
            "wf_tile1",
            "wf_tile2",
            "wf_pyramid",
            "wf_box",
            "wf_arrowplate",
            "wf_arrowplate_8dir",
            "wf_pressureplate",
            "wf_ringplate",
            "wf_blob",
            "wf_blob2",
            "wf_button",
            "wf_button_red",
            "wf_button_x");

    public static int of(HabboItem item) {
        if (item == null) {
            return NOBODY;
        }

        if (NO_USE_BUTTON.contains(baseName(item))) {
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
    /** The furni's catalog name in lower case, or "" when it has none. */
    public static String baseName(HabboItem item) {
        Item base = item != null ? item.getBaseItem() : null;
        String name = base != null ? base.getName() : null;
        return name != null ? name.toLowerCase(Locale.ROOT) : "";
    }

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
