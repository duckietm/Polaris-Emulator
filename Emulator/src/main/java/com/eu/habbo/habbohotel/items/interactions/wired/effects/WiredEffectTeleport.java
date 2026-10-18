package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredLegacyDataGuard;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.pets.RideablePet;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.rooms.RoomUnitType;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserEffectComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;
import com.eu.habbo.threading.runnables.RoomUnitTeleport;
import com.eu.habbo.threading.runnables.SendRoomUnitEffectComposer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class WiredEffectTeleport extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.TELEPORT;

    protected List<HabboItem> items;
    private boolean fastTeleport = false;
    private int furniSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredEffectTeleport(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.items = new ArrayList<>();
    }

    public WiredEffectTeleport(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.items = new ArrayList<>();
    }

    // Habbo's Teleport To, counted from the first 500 ms room boundary after the firing: the user is
    // sent again in place 1000 ms later and lands at 1500 ms; the effect ends at 3000 ms. Fast mode
    // lands on the next boundary and drops its effect 500 ms after that.
    static final int TELEPORT_EFFECT = 4;
    static final int FAST_TELEPORT_EFFECT = 235;
    static final long REPUBLISH_AFTER_BOUNDARY_MS = 1000L;
    static final long LAND_AFTER_BOUNDARY_MS = 1500L;
    static final long EFFECT_OFF_AFTER_BOUNDARY_MS = 3000L;
    static final long FAST_LAND_AFTER_BOUNDARY_MS = 500L;
    static final long FAST_EFFECT_OFF_AFTER_BOUNDARY_MS = 1000L;
    private static final long ROOM_CYCLE_MS = 500L;

    /** Until when a unit still glows from a teleport; a teleport during the glow sends no new effect. */
    private static final java.util.Map<RoomUnit, Long> GLOW_UNTIL =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /**
     * The grid delays from {@code now}: republish, landing and effect off. Normal: republished one second after the
     * boundary (3rd tick), lands at 1.5 s (4th), glow off at 3 s (7th). Fast: republished on the boundary (1st tick),
     * lands at 0.5 s (2nd), glow off at 1 s (3rd). Behaviour observed in plus-emulator/PlusEMU 2aa1704e and
     * e2fe8d15, reimplemented.
     */
    static long[] gridDelays(long boundary, long now, boolean fastTeleport) {
        long republish = boundary + (fastTeleport ? 0L : REPUBLISH_AFTER_BOUNDARY_MS) - now;
        long land = boundary + (fastTeleport ? FAST_LAND_AFTER_BOUNDARY_MS : LAND_AFTER_BOUNDARY_MS) - now;
        long effectOff =
                boundary + (fastTeleport ? FAST_EFFECT_OFF_AFTER_BOUNDARY_MS : EFFECT_OFF_AFTER_BOUNDARY_MS) - now;
        return new long[] {republish, land, effectOff};
    }

    /**
     * Starts the teleport glow unless the unit still glows from an earlier teleport: that one's end clears the glow,
     * and no new effect is sent. Behaviour observed in plus-emulator/PlusEMU 49c17ea2, reimplemented.
     */
    static boolean tryStartGlow(RoomUnit unit, long now, long durationMs) {
        synchronized (GLOW_UNTIL) {
            Long until = GLOW_UNTIL.get(unit);
            if (until != null && now < until) {
                return false;
            }
            GLOW_UNTIL.put(unit, now + Math.max(0L, durationMs));
            return true;
        }
    }

    /** Teleports with the classic timing (games, bots): effect 4 and a landing after the teleport delay. */
    public static void teleportUnitToTile(RoomUnit roomUnit, RoomTile tile) {
        teleportUnitToTile(roomUnit, tile, false, false);
    }

    /** Teleports like Habbo's Teleport To, on the room's 500 ms grid. */
    public static void teleportUnitToTile(RoomUnit roomUnit, RoomTile tile, boolean fastTeleport) {
        teleportUnitToTile(roomUnit, tile, fastTeleport, true);
    }

    /** The first 500 ms room-cycle boundary strictly after {@code now}. */
    static long nextRoomBoundary(long lastCycle, long now) {
        if (lastCycle <= 0 || lastCycle > now) {
            return now + ROOM_CYCLE_MS;
        }

        return lastCycle + (((now - lastCycle) / ROOM_CYCLE_MS) + 1) * ROOM_CYCLE_MS;
    }

    private static void teleportUnitToTile(RoomUnit roomUnit, RoomTile tile, boolean fastTeleport, boolean onRoomGrid) {
        if (roomUnit == null || tile == null || roomUnit.isWiredTeleporting) return;

        Room room = roomUnit.getRoom();

        if (room == null) {
            return;
        }

        // If this is a rider, sync the riding pet to the rider's current position immediately
        // Both will teleport together when the delay fires
        if (roomUnit.getRoomUnitType() == RoomUnitType.USER) {
            Habbo habbo = room.getHabbo(roomUnit);
            if (habbo != null
                    && habbo.getHabboInfo() != null
                    && habbo.getHabboInfo().getRiding() != null) {
                RideablePet ridingPet = habbo.getHabboInfo().getRiding();
                RoomUnit petUnit = ridingPet.getRoomUnit();
                if (petUnit != null) {
                    // Sync pet to rider's current position
                    RoomTile riderTile = roomUnit.getCurrentLocation();
                    petUnit.setLocation(riderTile);
                    petUnit.setZ(roomUnit.getZ() - 1.0);
                    petUnit.setPreviousLocation(riderTile);
                    petUnit.setGoalLocation(riderTile);
                    petUnit.removeStatus(RoomUnitStatus.MOVE);
                    petUnit.setCanWalk(false);
                    room.sendComposer(new RoomUserStatusComposer(petUnit).compose());
                }
            }
        }

        roomUnit.getRoom().unIdle(roomUnit.getRoom().getHabbo(roomUnit));

        long now = System.currentTimeMillis();
        long landingDelay;
        long effectDuration;
        long republishDelay = -1L;

        if (onRoomGrid) {
            long[] delays = gridDelays(nextRoomBoundary(room.getCycleTimestamp(), now), now, fastTeleport);
            republishDelay = delays[0];
            landingDelay = delays[1];
            effectDuration = delays[2];
        } else {
            landingDelay = getTeleportDelay(fastTeleport);
            effectDuration = getTeleportEffectDuration(fastTeleport);
        }

        int effect = (onRoomGrid && fastTeleport) ? FAST_TELEPORT_EFFECT : TELEPORT_EFFECT;
        if (!onRoomGrid || tryStartGlow(roomUnit, now, effectDuration)) {
            room.sendComposer(new RoomUserEffectComposer(roomUnit, effect).compose());
            schedule(new SendRoomUnitEffectComposer(room, roomUnit), effectDuration);
        }

        // Habbo's Teleport To runs the whole teleport for a user already on the target: the glow,
        // the republish and the landing. The classic timing (games, bots) still stops at the glow.
        if (!onRoomGrid && tile == roomUnit.getCurrentLocation()) {
            return;
        }

        // Fast mode ends the walk on the tile being stepped onto; normal mode lets it go on.
        if (onRoomGrid && fastTeleport) {
            roomUnit.setGoalLocation(roomUnit.getCurrentLocation());
        }

        tile = landingTile(room, tile);

        if (republishDelay >= 0) {
            schedule(
                    () -> {
                        if (room.isLoaded() && roomUnit.isInRoom()) {
                            room.sendComposer(new RoomUserStatusComposer(roomUnit).compose());
                        }
                    },
                    republishDelay);
        }

        schedule(
                () -> {
                    roomUnit.isWiredTeleporting = true;
                },
                Math.max(0, landingDelay - 500));
        schedule(
                new RoomUnitTeleport(
                        roomUnit,
                        room,
                        tile.x,
                        tile.y,
                        tile.getStackHeight() + (tile.state == RoomTileState.SIT ? -0.5 : 0),
                        roomUnit.getEffectId()),
                Math.max(0, landingDelay));
    }

    private static void schedule(Runnable task, long delay) {
        Emulator.getThreading().run(task, delay);
    }

    /**
     * A target nobody can stand on is not refused: the user lands on a free tile next to it, the
     * one to the north first.
     */
    private static RoomTile landingTile(Room room, RoomTile tile) {
        if (tile.state != RoomTileState.INVALID && tile.state != RoomTileState.BLOCKED) {
            return tile;
        }

        RoomTile north = room.getLayout().getTile(tile.x, (short) (tile.y - 1));
        if (north != null && north.state != RoomTileState.INVALID && north.state != RoomTileState.BLOCKED) {
            return north;
        }

        for (RoomTile optionalTile : room.getLayout().getTilesAround(tile)) {
            if (optionalTile != null
                    && optionalTile.state != RoomTileState.INVALID
                    && optionalTile.state != RoomTileState.BLOCKED) {
                return optionalTile;
            }
        }

        return tile;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        List<HabboItem> itemsSnapshot = new ArrayList<>(this.items);
        Set<HabboItem> items = new HashSet<>();

        for (HabboItem item : itemsSnapshot) {
            if (item.getRoomId() != this.getRoomId()
                    || Emulator.getGameEnvironment()
                                    .getRoomManager()
                                    .getRoom(this.getRoomId())
                                    .getHabboItem(item.getId())
                            == null) items.add(item);
        }

        for (HabboItem item : items) {
            this.items.remove(item);
        }
        itemsSnapshot = new ArrayList<>(this.items);
        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(itemsSnapshot.size());
        for (HabboItem item : itemsSnapshot) message.appendInt(item.getId());

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(1);
        message.appendInt(this.fastTeleport ? 1 : 0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        if (this.requiresTriggeringUser()) {
            List<Integer> invalidTriggers = new ArrayList<>();
            for (InteractionWiredTrigger object : room.getRoomSpecialTypes().getTriggers(this.getX(), this.getY())) {
                if (!object.isTriggeredByRoomUnit()) {
                    invalidTriggers.add(object.getBaseItem().getSpriteId());
                }
            }
            message.appendInt(invalidTriggers.size());
            for (Integer i : invalidTriggers) {
                message.appendInt(i);
            }
        } else {
            message.appendInt(0);
        }
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] params = settings.getIntParams();
        // Official layout: [fast]; the furni and user sources are the official source slots.
        this.fastTeleport = params.length > 0 && params[0] == 1;
        this.furniSource = settings.furniSource(0, WiredSourceUtil.SOURCE_TRIGGER);
        this.userSource = settings.userSource(0, WiredSourceUtil.SOURCE_TRIGGER);

        int itemsCount = settings.getFurniIds().length;

        if (itemsCount > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) {
            throw new WiredSaveException("Too many furni selected");
        }

        if (itemsCount > 0 && this.furniSource == WiredSourceUtil.SOURCE_TRIGGER) {
            this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
        }

        List<HabboItem> newItems = new ArrayList<>();

        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            for (int i = 0; i < itemsCount; i++) {
                int itemId = settings.getFurniIds()[i];
                HabboItem it = Emulator.getGameEnvironment()
                        .getRoomManager()
                        .getRoom(this.getRoomId())
                        .getHabboItem(itemId);

                if (it == null) throw new WiredSaveException(String.format("Item %s not found", itemId));

                newItems.add(it);
            }
        }

        int delay = settings.getDelay();

        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20))
            throw new WiredSaveException("Delay too long");

        this.items.clear();
        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            this.items.addAll(newItems);
        }
        this.setDelay(delay);

        return true;
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();

        if (room == null || room.getLayout() == null) {
            return;
        }

        List<HabboItem> effectiveItems = WiredSourceUtil.resolveItems(ctx, this.furniSource, this.items);
        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            this.items.removeIf(item -> item == null
                    || item.getRoomId() != this.getRoomId()
                    || Emulator.getGameEnvironment()
                                    .getRoomManager()
                                    .getRoom(this.getRoomId())
                                    .getHabboItem(item.getId())
                            == null);
        }

        if (effectiveItems.isEmpty()) return;

        for (RoomUnit roomUnit : WiredSourceUtil.resolveUsers(ctx, this.userSource)) {
            int i = Emulator.getRandom().nextInt(effectiveItems.size());
            HabboItem item = effectiveItems.get(i);

            if (item == null) continue;

            RoomTile tile = room.getLayout().getTile(item.getX(), item.getY());
            if (tile != null) {
                teleportUnitToTile(roomUnit, tile, this.fastTeleport);
            }
        }
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        List<HabboItem> itemsSnapshot = new ArrayList<>(this.items);
        return WiredManager.getGson()
                .toJson(new JsonData(
                        this.getDelay(),
                        itemsSnapshot.stream().map(HabboItem::getId).collect(Collectors.toList()),
                        this.fastTeleport,
                        this.furniSource,
                        this.userSource));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items = new ArrayList<>();
        String wiredData = set.getString("wired_data");

        JsonData jsonData = WiredMovementPayloadGuard.fromJson(wiredData, JsonData.class);
        if (jsonData != null) {
            this.setDelay(WiredMovementPayloadGuard.delay(jsonData.delay));
            this.fastTeleport = jsonData.fastTeleport;
            this.furniSource = WiredMovementPayloadGuard.furniSource(jsonData.furniSource);
            this.userSource = WiredMovementPayloadGuard.userSource(jsonData.userSource);
            if (jsonData.itemIds != null) {
                for (Integer id : jsonData.itemIds) {
                    if (id == null) continue;
                    HabboItem item = room.getHabboItem(id);
                    if (item != null) {
                        this.items.add(item);
                    }
                }
            }
            if (this.furniSource == WiredSourceUtil.SOURCE_TRIGGER && !this.items.isEmpty()) {
                this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
            }
        } else {
            String[] wiredDataOld = wiredData != null ? wiredData.split("\t") : new String[0];

            if (wiredDataOld.length >= 1) {
                this.setDelay(WiredLegacyDataGuard.parseDelay(wiredDataOld[0]));
            }
            if (wiredDataOld.length == 2) {
                if (wiredDataOld[1].contains(";")) {
                    this.items.addAll(WiredLegacyDataGuard.parseRoomItems(wiredDataOld[1], room));
                }
            }
            this.fastTeleport = false;
            this.furniSource = this.items.isEmpty() ? WiredSourceUtil.SOURCE_TRIGGER : WiredSourceUtil.SOURCE_SELECTED;
            this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        }
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.fastTeleport = false;
        this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.setDelay(0);
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public boolean requiresTriggeringUser() {
        return this.userSource == WiredSourceUtil.SOURCE_TRIGGER;
    }

    @Override
    protected long requiredCooldown() {
        return COOLDOWN_DEFAULT;
    }

    private static int getTeleportDelay(boolean fastTeleport) {
        return fastTeleport ? Math.max(75, WiredManager.TELEPORT_DELAY / 5) : WiredManager.TELEPORT_DELAY;
    }

    private static int getTeleportEffectDuration(boolean fastTeleport) {
        return fastTeleport
                ? Math.max(300, (WiredManager.TELEPORT_DELAY + 1000) / 3)
                : (WiredManager.TELEPORT_DELAY + 1000);
    }

    static class JsonData {
        int delay;
        List<Integer> itemIds;
        boolean fastTeleport;
        int furniSource;
        int userSource;

        public JsonData(int delay, List<Integer> itemIds, boolean fastTeleport, int furniSource, int userSource) {
            this.delay = delay;
            this.itemIds = itemIds;
            this.fastTeleport = fastTeleport;
            this.furniSource = furniSource;
            this.userSource = userSource;
        }
    }
}
