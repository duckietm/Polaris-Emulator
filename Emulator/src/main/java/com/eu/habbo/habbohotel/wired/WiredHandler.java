package com.eu.habbo.habbohotel.wired;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectGiveReward;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.google.gson.GsonBuilder;
import java.util.Collection;

/**
 * Released wired compatibility facade.
 *
 * <p>Plugins may still link to these fields and methods, so their descriptors remain unchanged.
 * Gameplay execution is delegated to the single guarded {@link WiredManager} runtime.
 */
public class WiredHandler {

    // Configuration fields retained exactly for released plugin binary compatibility.
    public static int MAXIMUM_FURNI_SELECTION = Emulator.getConfig().getInt("hotel.wired.furni.selection.count", 5);
    public static int TELEPORT_DELAY = Emulator.getConfig().getInt("wired.effect.teleport.delay", 500);

    public WiredHandler() {}

    public static boolean handle(WiredTriggerType triggerType, RoomUnit roomUnit, Room room, Object[] stuff) {
        WiredLegacyUsageTelemetry.record(WiredLegacyUsageTelemetry.Operation.HANDLE_TRIGGER_TYPE, room);
        return WiredLegacyCompatibilityAdapter.handle(triggerType, roomUnit, room, stuff);
    }

    public static boolean handleCustomTrigger(
            Class<? extends InteractionWiredTrigger> triggerType, RoomUnit roomUnit, Room room, Object[] stuff) {
        WiredLegacyUsageTelemetry.record(WiredLegacyUsageTelemetry.Operation.HANDLE_CUSTOM_TRIGGER, room);
        return WiredLegacyCompatibilityAdapter.handleCustomTrigger(triggerType, roomUnit, room, stuff);
    }

    public static boolean handle(
            InteractionWiredTrigger trigger, final RoomUnit roomUnit, final Room room, final Object[] stuff) {
        WiredLegacyUsageTelemetry.record(WiredLegacyUsageTelemetry.Operation.HANDLE_TRIGGER_ITEM, room);
        return WiredLegacyCompatibilityAdapter.handle(trigger, roomUnit, room, stuff);
    }

    public static GsonBuilder getGsonBuilder() {
        return WiredManager.getGsonBuilder();
    }

    public static boolean executeEffectsAtTiles(
            Collection<RoomTile> tiles, final RoomUnit roomUnit, final Room room, final Object[] stuff) {
        WiredLegacyUsageTelemetry.record(WiredLegacyUsageTelemetry.Operation.EXECUTE_EFFECTS_AT_TILES, room);
        return WiredLegacyCompatibilityAdapter.executeEffectsAtTiles(tiles, roomUnit, room, stuff);
    }

    public static void dropRewards(int wiredId) {
        WiredLegacyUsageTelemetry.record(WiredLegacyUsageTelemetry.Operation.DROP_REWARDS, null);
        WiredManager.dropRewards(wiredId);
    }

    public static boolean getReward(Habbo habbo, WiredEffectGiveReward wiredBox) {
        WiredLegacyUsageTelemetry.record(WiredLegacyUsageTelemetry.Operation.GET_REWARD, null);
        return WiredManager.getReward(habbo, wiredBox);
    }

    public static void resetTimers(Room room) {
        WiredLegacyUsageTelemetry.record(WiredLegacyUsageTelemetry.Operation.RESET_TIMERS, room);
        WiredLegacyCompatibilityAdapter.resetTimers(room);
    }
}
