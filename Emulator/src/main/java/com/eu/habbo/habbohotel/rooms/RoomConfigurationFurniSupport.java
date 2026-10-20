package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.rooms.items.AreaHideComposer;

/**
 * Room-wide refreshes for the room configuration furni ({@code conf_*}) when one leaves the room.
 *
 * <p>Their effect is room state the clients hold (a floor hole, the hand item buttons, hidden
 * invisible layers), so a controller picked up while switched on has to tell the room that its
 * effect is gone; otherwise the room keeps it until everybody reloads.
 */
public final class RoomConfigurationFurniSupport {
    private RoomConfigurationFurniSupport() {}

    /** Call after {@code item} was taken out of the room's item index. */
    public static void onControllerRemoved(Room room, HabboItem item) {
        if (room == null || item == null) {
            return;
        }

        if (RoomAreaHideSupport.isControllerActive(item)) {
            room.sendComposer(new AreaHideComposer(item, false).compose());
        }

        if (RoomHanditemBlockSupport.isActiveController(item)) {
            RoomHanditemBlockSupport.sendState(room);
        } else if (RoomConfInvisSupport.isControllerActive(item)) {
            RoomConfInvisSupport.sendState(room);
        } else if (RoomChooserDisableSupport.isActiveController(item)) {
            RoomHanditemBlockSupport.broadcastConfigurationItemStates(room);
        }
    }
}
