package polaris.hotel.items;

import java.util.Locale;
import java.util.Set;
import polaris.hotel.items.interactions.InteractionInformationTerminal;
import polaris.hotel.items.interactions.InteractionPostIt;
import polaris.hotel.items.interactions.InteractionPuzzleBox;
import polaris.hotel.items.interactions.InteractionRandomState;
import polaris.hotel.items.interactions.InteractionSwitch;
import polaris.hotel.items.interactions.InteractionSwitchRemoteControl;
import polaris.hotel.items.interactions.InteractionTeleport;
import polaris.hotel.items.interactions.InteractionVendingMachine;
import polaris.hotel.items.interactions.InteractionWiredRoomLinker;
import polaris.hotel.users.UserItem;

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

    public static int of(UserItem item) {
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
    public static String baseName(UserItem item) {
        Item base = item != null ? item.getBaseItem() : null;
        String name = base != null ? base.getName() : null;
        return name != null ? name.toLowerCase(Locale.ROOT) : "";
    }

    public static boolean isUsableByEverybody(UserItem item) {
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
