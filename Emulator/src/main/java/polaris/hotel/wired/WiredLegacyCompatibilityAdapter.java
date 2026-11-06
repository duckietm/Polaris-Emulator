package polaris.hotel.wired;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import polaris.WiredPlatform;
import polaris.hotel.items.interactions.InteractionWiredTrigger;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.rooms.RoomWiredDisableSupport;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.api.WiredStack;
import polaris.hotel.wired.core.RoomWiredStackIndex;
import polaris.hotel.wired.core.WiredEngine;
import polaris.hotel.wired.core.WiredEvent;
import polaris.hotel.wired.core.WiredManager;
import polaris.hotel.wired.migrate.WiredEvents;

/** Routes released {@link WiredHandler} calls through the single modern wired runtime. */
final class WiredLegacyCompatibilityAdapter {

    private WiredLegacyCompatibilityAdapter() {}

    static boolean handle(WiredTriggerType triggerType, RoomUnit roomUnit, Room room, Object[] stuff) {
        if (triggerType == null || triggerType == WiredTriggerType.CUSTOM || !canDispatch(room)) {
            return false;
        }

        return WiredManager.triggerFromLegacy(triggerType, roomUnit, room, stuff);
    }

    static boolean handleCustomTrigger(
            Class<? extends InteractionWiredTrigger> triggerType, RoomUnit roomUnit, Room room, Object[] stuff) {
        if (triggerType == null || !canDispatch(room)) {
            return false;
        }

        WiredEvent event = WiredEvents.fromLegacy(WiredTriggerType.CUSTOM, room, roomUnit, stuff);
        return dispatchMatchingSources(
                room,
                event,
                stack -> stack.triggerItem() != null && stack.triggerItem().getClass() == triggerType);
    }

    static boolean handle(InteractionWiredTrigger trigger, RoomUnit roomUnit, Room room, Object[] stuff) {
        if (trigger == null || !canDispatch(room)) {
            return false;
        }

        WiredEvent event = WiredEvents.fromLegacy(trigger.getType(), room, roomUnit, stuff);
        return dispatchMatchingSources(
                room,
                event,
                stack -> stack.triggerItem() != null && stack.triggerItem().getId() == trigger.getId());
    }

    static boolean executeEffectsAtTiles(Collection<RoomTile> tiles, RoomUnit roomUnit, Room room, Object[] stuff) {
        Objects.requireNonNull(tiles, "tiles");
        WiredManager.executeEffectsAtTiles(tiles, roomUnit, room, 0);

        // The released facade always acknowledged a non-null tile collection, even when no effect ran.
        return true;
    }

    static void resetTimers(Room room) {
        Objects.requireNonNull(room, "room");
        if (!room.isLoaded() || room.getRoomSpecialTypes() == null) {
            return;
        }

        WiredManager.resetTimers(room);
    }

    private static boolean dispatchMatchingSources(Room room, WiredEvent event, Predicate<WiredStack> predicate) {
        WiredEngine engine = WiredManager.getEngine();
        RoomWiredStackIndex index = WiredManager.getStackIndex();
        if (engine == null || index == null) {
            return false;
        }

        List<WiredStack> stacks = index.getStacks(room, event.getType());
        if (stacks.isEmpty()) {
            return false;
        }

        boolean handled = false;
        Set<Long> dispatchedTiles = new HashSet<>();
        for (WiredStack stack : stacks) {
            if (stack == null || !predicate.test(stack)) {
                continue;
            }

            UserItem source = stack.triggerItem();
            long tileKey = toTileCoordinateKey(source.getX(), source.getY());
            if (!dispatchedTiles.add(tileKey)) {
                continue;
            }

            handled = engine.handleEventForSourceItem(event, source.getId()) || handled;
        }

        return handled;
    }

    private static boolean canDispatch(Room room) {
        return WiredPlatform.isReady()
                && room != null
                && room.isLoaded()
                && room.getRoomSpecialTypes() != null
                && !RoomWiredDisableSupport.isWiredDisabled(room)
                && WiredManager.isEnabled();
    }

    private static long toTileCoordinateKey(int x, int y) {
        return (((long) x) << 32) | (y & 0xffffffffL);
    }
}
