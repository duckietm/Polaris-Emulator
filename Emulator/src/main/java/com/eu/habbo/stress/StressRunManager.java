package com.eu.habbo.stress;

import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.ItemManager;
import com.eu.habbo.habbohotel.items.interactions.InteractionRoller;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomManager;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitType;
import com.eu.habbo.habbohotel.rooms.RoomUserRotation;
import com.eu.habbo.habbohotel.users.HabboGender;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.outgoing.rooms.items.RoomFloorItemsComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUsersComposer;
import com.eu.habbo.threading.ThreadPooling;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class StressRunManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(StressRunManager.class);
    private static final String BOT_FIGURE = "hr-100-61.hd-180-1.ch-210-66.lg-270-82.sh-290-80.ha-1002-63";
    private static final long CHAT_TICK_MS = 100L;
    private static final long WIRED_TICK_MS = 100L;

    private final Map<Integer, StressRun> runs = new ConcurrentHashMap<>();
    private final AtomicInteger nextBotId = new AtomicInteger(Integer.MAX_VALUE - 10_000);
    private final AtomicInteger nextItemId = new AtomicInteger(Integer.MAX_VALUE - 1_000_000);
    private final RoomManager roomManager;
    private final ItemManager itemManager;
    private final ThreadPooling threading;
    private final StressLimits limits;

    public StressRunManager(
            RoomManager roomManager, ItemManager itemManager, ThreadPooling threading, StressLimits limits) {
        this.roomManager = roomManager;
        this.itemManager = itemManager;
        this.threading = threading;
        this.limits = limits;
    }

    public synchronized StressRunSnapshot start(StressScenario scenario) {
        scenario.validate(this.limits);

        StressRun existing = this.runs.get(scenario.roomId());
        if (existing != null && existing.isRunning()) {
            throw new IllegalStateException("room already has an active stress run");
        }

        StressRun run = new StressRun(scenario);
        this.runs.put(scenario.roomId(), run);
        ScheduledFuture<?> provisionTask = this.threading.run(() -> this.provision(run));
        if (provisionTask == null) {
            run.fail("emulator task scheduler is unavailable");
        }
        return run.snapshot();
    }

    public StressRunSnapshot status(int roomId) {
        StressRun run = this.runs.get(roomId);
        if (run == null) {
            throw new IllegalStateException("room has no stress run");
        }
        return run.snapshot();
    }

    public StressRunSnapshot stop(int roomId) {
        StressRun run = this.runs.get(roomId);
        if (run == null) {
            throw new IllegalStateException("room has no stress run");
        }
        this.requestStop(run);
        return run.snapshot();
    }

    public List<StressRunSnapshot> snapshots() {
        return this.runs.values().stream()
                .map(StressRun::snapshot)
                .sorted(Comparator.comparingInt(StressRunSnapshot::roomId))
                .toList();
    }

    private void provision(StressRun run) {
        try {
            Room room = this.roomManager.loadRoom(run.scenario.roomId(), true);
            if (room == null) {
                throw new IllegalStateException("room not found");
            }
            if (!room.isLoaded() || room.getLayout() == null) {
                throw new IllegalStateException("room did not finish loading");
            }

            run.room = room;
            run.roomName = room.getName();
            run.previousPreventUncaching = room.preventUncaching;
            room.preventUncaching = true;
            if (run.scenario.items() > 0 || run.scenario.rollers() > 0 || run.scenario.wiredStacks() > 0) {
                run.ownerNameTouched = true;
                run.ownerNameWasPresent = room.getFurniOwnerNames().containsKey(room.getOwnerId());
                run.previousOwnerName = room.getFurniOwnerNames().get(room.getOwnerId());
                room.getFurniOwnerNames().put(room.getOwnerId(), room.getOwnerName());
            }

            Item baseItem = null;
            List<RoomTile> itemTiles = List.of();
            if (run.scenario.items() > 0 || run.scenario.rollers() > 0) {
                baseItem = resolveBaseItem(run.scenario.itemId());
                itemTiles = itemTiles(room, baseItem);
                Collections.shuffle(itemTiles, new Random(run.scenario.seed() ^ 0x5DEECE66DL));
                if (itemTiles.isEmpty()) {
                    throw new IllegalStateException("room has no tiles that fit the selected item");
                }
            }

            if (run.scenario.rollers() > 0 && !run.stopRequested.get()) {
                this.provisionRollers(run, baseItem, rollerLanes(room));
            }

            List<RoomTile> botTiles = botTiles(room);
            Collections.shuffle(botTiles, new Random(run.scenario.seed()));
            if (run.scenario.bots() > 0 && botTiles.isEmpty()) {
                throw new IllegalStateException("room has no open tiles for bots");
            }

            for (int i = 0; i < run.scenario.bots() && !run.stopRequested.get(); i++) {
                Bot bot = this.createBot(run, botTiles.get(i % botTiles.size()), i);
                room.getUnitManager().addBot(bot);
                run.bots.add(bot);
                run.activeBots.incrementAndGet();
            }

            for (int i = 0; i < run.scenario.items() && !run.stopRequested.get(); i++) {
                this.addOwnedItem(run, this.createItem(run, baseItem, itemTiles, i), ItemKind.GENERIC);
            }

            if (run.scenario.wiredStacks() > 0 && !run.stopRequested.get()) {
                this.provisionWiredStacks(run);
            }
            room.getItemManager().tileCache.clear();

            if (run.stopRequested.get()) {
                this.cleanup(run);
                return;
            }

            this.broadcastProvisionedEntities(run);
            if (run.rollerSpeedOverrideChanged) {
                room.setTransientRollerSpeedOverride(0);
            }
            run.state = RunState.ACTIVE;
            this.scheduleChat(run);
            this.scheduleWiredEvents(run);
            this.scheduleAutomaticStop(run);
            LOGGER.warn(
                    "Stress run {} active in room {}: {} bots, {} items, {} rollers, {} wired stacks, "
                            + "{} chat messages/second, {} wired events/second",
                    run.runId,
                    room.getId(),
                    run.activeBots.get(),
                    run.activeItems.get(),
                    run.activeRollers.get(),
                    run.activeWiredStacks.get(),
                    run.scenario.chatPerSecond(),
                    run.scenario.wiredEventsPerSecond());

            if (run.stopRequested.get()) {
                this.requestCleanup(run);
            }
        } catch (Exception exception) {
            LOGGER.error("Failed to provision stress run {}", run.runId, exception);
            run.fail(safeMessage(exception));
            this.cleanup(run);
        }
    }

    private Bot createBot(StressRun run, RoomTile tile, int index) {
        Room room = run.room;
        int id = uniqueBotId(room);
        Bot bot = new Bot(
                id,
                "LoadBot-" + (index + 1),
                "Polaris stress run " + run.runId.substring(0, 8),
                BOT_FIGURE,
                index % 2 == 0 ? HabboGender.M : HabboGender.F,
                room.getOwnerId(),
                room.getOwnerName());
        RoomUnit unit = new RoomUnit();
        unit.setRoomUnitType(RoomUnitType.BOT);
        unit.setRoom(room);
        unit.setLocation(tile);
        unit.setZ(tile.getStackHeight());
        unit.setPreviousLocationZ(tile.getStackHeight());
        unit.setRotation(RoomUserRotation.fromValue(index % 8));
        unit.setCanWalk(run.scenario.movement());
        unit.setInRoom(true);
        unit.setPathFinderRoom(room);
        bot.setRoom(room);
        bot.setRoomUnit(unit);
        bot.setCanWalk(run.scenario.movement());
        return bot;
    }

    private HabboItem createItem(StressRun run, Item baseItem, List<RoomTile> tiles, int index) {
        RoomTile tile = tiles.get(index % tiles.size());
        int layer = index / tiles.size();
        HabboItem item = new StressTransientItem(uniqueItemId(run.room), run.room.getOwnerId(), baseItem);
        item.setRoomId(run.room.getId());
        item.setX(tile.x);
        item.setY(tile.y);
        item.setZ(tile.z + Math.min(9_000D, layer * Math.max(0.01D, baseItem.getHeight())));
        item.setRotation((index % 4) * 2);
        item.needsUpdate(false);
        return item;
    }

    private void provisionRollers(StressRun run, Item cargoBaseItem, List<RollerLane> lanes) {
        Item rollerBaseItem =
                resolveInteractionItem("roller", InteractionRoller.class, "hotel has no roller furniture definition");
        Collections.shuffle(lanes, new Random(run.scenario.seed() ^ 0x6A09E667F3BCC909L));
        if (lanes.isEmpty()) {
            throw new IllegalStateException("room has no usable roller lanes");
        }

        run.previousRollerSpeedOverride = run.room.getTransientRollerSpeedOverride();
        run.rollerSpeedOverrideChanged = true;
        run.room.setTransientRollerSpeedOverride(-1);

        for (int i = 0; i < run.scenario.rollers() && !run.stopRequested.get(); i++) {
            RollerLane lane = lanes.get(i % lanes.size());
            int layer = i / lanes.size();
            StressTransientRoller roller =
                    new StressTransientRoller(uniqueItemId(run.room), run.room.getOwnerId(), rollerBaseItem);
            placeFloorItem(
                    roller,
                    run.room,
                    lane.tile(),
                    lane.rotation(),
                    lane.tile().z + Math.min(9_000D, layer * Math.max(0.01D, rollerBaseItem.getHeight())));
            this.addOwnedItem(run, roller, ItemKind.ROLLER);
        }

        int cargoCount = Math.min(run.scenario.rollers(), lanes.size());
        for (int i = 0; i < cargoCount && !run.stopRequested.get(); i++) {
            RollerLane lane = lanes.get(i);
            StressTransientItem cargo =
                    new StressTransientItem(uniqueItemId(run.room), run.room.getOwnerId(), cargoBaseItem);
            placeFloorItem(
                    cargo, run.room, lane.tile(), 0, lane.tile().z + Math.max(0.01D, rollerBaseItem.getHeight()));
            this.addOwnedItem(run, cargo, ItemKind.AUXILIARY);
        }
    }

    private void provisionWiredStacks(StressRun run) {
        if (!WiredManager.isEnabled()) {
            throw new IllegalStateException("wired engine is unavailable");
        }

        Item triggerBase = resolveInteractionItem(
                "wf_trg_game_starts", InteractionWiredTrigger.class, "hotel has no wired trigger furniture definition");
        Item effectBase = resolveInteractionItem(
                "wf_act_log", InteractionWiredEffect.class, "hotel has no wired effect furniture definition");
        List<RoomTile> tiles = itemTiles(run.room, triggerBase);
        tiles.removeIf(tile -> !fits(
                run.room, tile.x, tile.y, Math.max(1, effectBase.getWidth()), Math.max(1, effectBase.getLength())));
        Collections.shuffle(tiles, new Random(run.scenario.seed() ^ 0xBB67AE8584CAA73BL));
        if (tiles.isEmpty()) {
            throw new IllegalStateException("room has no tiles that fit wired furniture");
        }

        int effectCount = Math.min(run.scenario.wiredStacks(), tiles.size());
        for (int i = 0; i < effectCount && !run.stopRequested.get(); i++) {
            RoomTile tile = tiles.get(i);
            StressWiredEffect effect = new StressWiredEffect(
                    uniqueItemId(run.room), run.room.getOwnerId(), effectBase, run.wiredEffectsExecuted);
            placeFloorItem(effect, run.room, tile, 0, tile.z);
            this.addOwnedItem(run, effect, ItemKind.AUXILIARY);
        }

        for (int i = 0; i < run.scenario.wiredStacks() && !run.stopRequested.get(); i++) {
            RoomTile tile = tiles.get(i % effectCount);
            int layer = i / effectCount;
            StressWiredTrigger trigger =
                    new StressWiredTrigger(uniqueItemId(run.room), run.room.getOwnerId(), triggerBase);
            placeFloorItem(
                    trigger,
                    run.room,
                    tile,
                    0,
                    tile.z + Math.min(9_000D, layer * Math.max(0.01D, triggerBase.getHeight())));
            this.addOwnedItem(run, trigger, ItemKind.WIRED_STACK);
        }
    }

    private void addOwnedItem(StressRun run, HabboItem item, ItemKind kind) {
        run.room.getItemManager().addHabboItem(item);
        run.items.add(new OwnedItem(item, kind));
        switch (kind) {
            case GENERIC -> run.activeItems.incrementAndGet();
            case ROLLER -> run.activeRollers.incrementAndGet();
            case WIRED_STACK -> run.activeWiredStacks.incrementAndGet();
            case AUXILIARY -> {
                // Auxiliary cargo and shared effects are included in cleanup but not headline counts.
            }
        }
    }

    private static void placeFloorItem(HabboItem item, Room room, RoomTile tile, int rotation, double height) {
        item.setRoomId(room.getId());
        item.setX(tile.x);
        item.setY(tile.y);
        item.setZ(height);
        item.setRotation(rotation);
        item.needsUpdate(false);
    }

    private void broadcastProvisionedEntities(StressRun run) {
        if (run.room.getUserCount() == 0) {
            return;
        }
        if (!run.bots.isEmpty()) {
            run.room.sendComposer(new RoomUsersComposer(List.copyOf(run.bots), true).compose());
        }
        if (!run.items.isEmpty()) {
            run.room.sendComposer(new RoomFloorItemsComposer(
                            run.room.getFurniOwnerNames(),
                            run.room.getItemManager().getFloorItems())
                    .compose());
        }
    }

    private void scheduleChat(StressRun run) {
        if (run.scenario.chatPerSecond() == 0 || run.bots.isEmpty()) {
            return;
        }
        run.chatTask = this.threading
                .getService()
                .scheduleAtFixedRate(() -> this.sendChatBurst(run), CHAT_TICK_MS, CHAT_TICK_MS, TimeUnit.MILLISECONDS);
    }

    private void scheduleWiredEvents(StressRun run) {
        if (run.scenario.wiredEventsPerSecond() == 0 || run.activeWiredStacks.get() == 0) {
            return;
        }
        run.wiredTask = this.threading
                .getService()
                .scheduleAtFixedRate(
                        () -> this.sendWiredBurst(run), WIRED_TICK_MS, WIRED_TICK_MS, TimeUnit.MILLISECONDS);
    }

    private void sendWiredBurst(StressRun run) {
        if (run.state != RunState.ACTIVE || run.stopRequested.get()) {
            return;
        }

        long budget = run.wiredEventBudget.addAndGet(run.scenario.wiredEventsPerSecond());
        int events = (int) (budget / (1_000L / WIRED_TICK_MS));
        if (events == 0) {
            return;
        }
        run.wiredEventBudget.addAndGet(-(long) events * (1_000L / WIRED_TICK_MS));

        synchronized (run.wiredExecutionLock) {
            if (run.state != RunState.ACTIVE || run.stopRequested.get()) {
                return;
            }
            for (int i = 0; i < events && !run.stopRequested.get(); i++) {
                WiredManager.triggerGameStarts(run.room);
                run.wiredEventsFired.incrementAndGet();
            }
        }
    }

    private void sendChatBurst(StressRun run) {
        if (run.state != RunState.ACTIVE || run.stopRequested.get()) {
            return;
        }

        long budget = run.chatBudget.addAndGet(run.scenario.chatPerSecond());
        int messages = (int) (budget / (1_000L / CHAT_TICK_MS));
        if (messages == 0) {
            return;
        }
        run.chatBudget.addAndGet(-(long) messages * (1_000L / CHAT_TICK_MS));

        for (int i = 0; i < messages && !run.stopRequested.get(); i++) {
            long sequence = run.messagesSent.incrementAndGet();
            Bot bot = run.bots.get((int) (sequence % run.bots.size()));
            bot.talk("Stress message " + sequence + " [" + run.runId.substring(0, 8) + "]");
        }
    }

    private void scheduleAutomaticStop(StressRun run) {
        if (run.scenario.durationSeconds() <= 0) {
            return;
        }
        run.durationTask = this.threading
                .getService()
                .schedule(() -> this.requestStop(run), run.scenario.durationSeconds(), TimeUnit.SECONDS);
    }

    private void requestStop(StressRun run) {
        run.stopRequested.set(true);
        if (run.room != null && run.rollerSpeedOverrideChanged) {
            run.room.setTransientRollerSpeedOverride(-1);
        }
        if (run.state == RunState.ACTIVE) {
            this.requestCleanup(run);
        }
    }

    private void requestCleanup(StressRun run) {
        if (!run.cleanupStarted.compareAndSet(false, true)) {
            return;
        }
        run.state = RunState.STOPPING;
        cancel(run.chatTask);
        cancel(run.wiredTask);
        cancel(run.durationTask);
        this.threading.run(() -> this.cleanupEntities(run));
    }

    private void cleanup(StressRun run) {
        if (!run.cleanupStarted.compareAndSet(false, true)) {
            return;
        }
        cancel(run.chatTask);
        cancel(run.wiredTask);
        cancel(run.durationTask);
        this.cleanupEntities(run);
    }

    private void cleanupEntities(StressRun run) {
        Room room = run.room;
        if (room != null) {
            synchronized (run.wiredExecutionLock) {
                synchronized (room) {
                    for (Bot bot : run.bots) {
                        try {
                            if (room.getUnitManager().removeBot(bot)) {
                                run.activeBots.decrementAndGet();
                            }
                        } catch (Exception exception) {
                            LOGGER.warn(
                                    "Failed to remove stress bot {} from room {}",
                                    bot.getId(),
                                    room.getId(),
                                    exception);
                        }
                    }
                    for (OwnedItem owned : run.items) {
                        HabboItem item = owned.item();
                        try {
                            item.needsUpdate(false);
                            item.needsDelete(false);
                            room.getItemManager().removeHabboItem(item);
                            if (room.getHabboItem(item.getId()) == null) {
                                run.decrement(owned.kind());
                            }
                        } catch (Exception exception) {
                            LOGGER.warn(
                                    "Failed to remove stress item {} from room {}",
                                    item.getId(),
                                    room.getId(),
                                    exception);
                        }
                    }
                    room.getItemManager().tileCache.clear();
                    if (run.rollerSpeedOverrideChanged) {
                        room.setTransientRollerSpeedOverride(run.previousRollerSpeedOverride);
                    }
                    if (run.ownerNameTouched) {
                        if (run.ownerNameWasPresent) {
                            room.getFurniOwnerNames().put(room.getOwnerId(), run.previousOwnerName);
                        } else if (room.getFurniOwnerCount().get(room.getOwnerId()) == 0) {
                            room.getFurniOwnerNames().remove(room.getOwnerId());
                        }
                    }
                    room.preventUncaching = run.previousPreventUncaching;

                    if (!run.items.isEmpty() && room.getUserCount() > 0) {
                        room.sendComposer(new RoomFloorItemsComposer(
                                        room.getFurniOwnerNames(),
                                        room.getItemManager().getFloorItems())
                                .compose());
                    }
                }
            }
        }

        run.finishedAtEpochMs = System.currentTimeMillis();
        if (run.state != RunState.FAILED) {
            run.state = RunState.COMPLETED;
        }
        LOGGER.warn("Stress run {} cleaned up", run.runId);
    }

    private static List<RoomTile> botTiles(Room room) {
        List<RoomTile> tiles = new ArrayList<>();
        for (short y = 0; y < room.getLayout().getMapSizeY(); y++) {
            for (short x = 0; x < room.getLayout().getMapSizeX(); x++) {
                RoomTile tile = room.getLayout().getTile(x, y);
                if (tile != null && tile.state == RoomTileState.OPEN) {
                    tiles.add(tile);
                }
            }
        }
        return tiles;
    }

    private static List<RoomTile> itemTiles(Room room, Item item) {
        List<RoomTile> tiles = new ArrayList<>();
        int width = Math.max(1, item.getWidth());
        int length = Math.max(1, item.getLength());
        for (short y = 0; y < room.getLayout().getMapSizeY(); y++) {
            for (short x = 0; x < room.getLayout().getMapSizeX(); x++) {
                if (fits(room, x, y, width, length)) {
                    tiles.add(room.getLayout().getTile(x, y));
                }
            }
        }
        return tiles;
    }

    private static boolean fits(Room room, short x, short y, int width, int length) {
        for (int offsetY = 0; offsetY < length; offsetY++) {
            for (int offsetX = 0; offsetX < width; offsetX++) {
                RoomTile tile = room.getLayout().getTile((short) (x + offsetX), (short) (y + offsetY));
                if (tile == null || tile.state == RoomTileState.INVALID) {
                    return false;
                }
            }
        }
        return true;
    }

    private Item resolveBaseItem(int requestedId) {
        if (requestedId > 0) {
            Item requested = this.itemManager.getItem(requestedId);
            if (requested == null) {
                throw new IllegalArgumentException("item base not found");
            }
            if (requested.getType() != FurnitureType.FLOOR) {
                throw new IllegalArgumentException("selected item must be floor furniture");
            }
            return requested;
        }

        Collection<Item> items = this.itemManager.getItems().values();
        return items.stream()
                .filter(item -> item != null && item.getType() == FurnitureType.FLOOR)
                .filter(item -> item.getWidth() == 1 && item.getLength() == 1)
                .min(Comparator.comparingInt(Item::getId))
                .orElseThrow(() -> new IllegalStateException("hotel has no suitable floor furniture"));
    }

    private Item resolveInteractionItem(
            String interactionName, Class<? extends HabboItem> interactionType, String missingMessage) {
        List<Item> candidates = this.itemManager.getItems().values().stream()
                .filter(item -> item != null && item.getType() == FurnitureType.FLOOR)
                .filter(item -> item.getInteractionType() != null)
                .toList();
        return candidates.stream()
                .filter(item -> interactionName.equalsIgnoreCase(
                        item.getInteractionType().getName()))
                .min(Comparator.comparingInt(Item::getId))
                .or(() -> candidates.stream()
                        .filter(item -> interactionType.isAssignableFrom(
                                item.getInteractionType().getType()))
                        .min(Comparator.comparingInt(Item::getId)))
                .orElseThrow(() -> new IllegalStateException(missingMessage));
    }

    private static List<RollerLane> rollerLanes(Room room) {
        List<RollerLane> lanes = new ArrayList<>();
        int[] rotations = {0, 2, 4, 6};
        for (short y = 0; y < room.getLayout().getMapSizeY(); y++) {
            for (short x = 0; x < room.getLayout().getMapSizeX(); x++) {
                RoomTile tile = room.getLayout().getTile(x, y);
                if (tile == null
                        || tile.state != RoomTileState.OPEN
                        || tile.hasUnits()
                        || !room.getItemsAt(tile).isEmpty()) {
                    continue;
                }
                for (int rotation : rotations) {
                    RoomTile destination = room.getLayout().getTileInFront(tile, rotation);
                    if (destination != null
                            && destination.state != RoomTileState.INVALID
                            && !destination.hasUnits()
                            && room.getItemsAt(destination).isEmpty()) {
                        lanes.add(new RollerLane(tile, rotation));
                        break;
                    }
                }
            }
        }
        return lanes;
    }

    private int uniqueBotId(Room room) {
        int candidate;
        do {
            candidate = this.nextBotId.getAndDecrement();
        } while (candidate <= 0 || room.getCurrentBots().containsKey(candidate));
        return candidate;
    }

    private int uniqueItemId(Room room) {
        int candidate;
        do {
            candidate = this.nextItemId.getAndDecrement();
        } while (candidate <= 0 || room.getItemManager().getRoomItems().containsKey(candidate));
        return candidate;
    }

    private static void cancel(ScheduledFuture<?> future) {
        if (future != null) {
            future.cancel(false);
        }
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private enum RunState {
        STARTING,
        ACTIVE,
        STOPPING,
        COMPLETED,
        FAILED
    }

    private enum ItemKind {
        GENERIC,
        ROLLER,
        WIRED_STACK,
        AUXILIARY
    }

    private record OwnedItem(HabboItem item, ItemKind kind) {}

    private record RollerLane(RoomTile tile, int rotation) {}

    private static final class StressRun {
        private final String runId = UUID.randomUUID().toString();
        private final StressScenario scenario;
        private final long startedAtEpochMs = System.currentTimeMillis();
        private final List<Bot> bots = new ArrayList<>();
        private final List<OwnedItem> items = new ArrayList<>();
        private final AtomicInteger activeBots = new AtomicInteger();
        private final AtomicInteger activeItems = new AtomicInteger();
        private final AtomicInteger activeRollers = new AtomicInteger();
        private final AtomicInteger activeWiredStacks = new AtomicInteger();
        private final AtomicLong messagesSent = new AtomicLong();
        private final AtomicLong chatBudget = new AtomicLong();
        private final AtomicLong wiredEventsFired = new AtomicLong();
        private final AtomicLong wiredEffectsExecuted = new AtomicLong();
        private final AtomicLong wiredEventBudget = new AtomicLong();
        private final AtomicBoolean stopRequested = new AtomicBoolean();
        private final AtomicBoolean cleanupStarted = new AtomicBoolean();
        private final Object wiredExecutionLock = new Object();
        private volatile RunState state = RunState.STARTING;
        private volatile Room room;
        private volatile String roomName = "";
        private volatile String error = "";
        private volatile long finishedAtEpochMs;
        private volatile boolean previousPreventUncaching;
        private volatile Integer previousRollerSpeedOverride;
        private volatile boolean rollerSpeedOverrideChanged;
        private volatile boolean ownerNameTouched;
        private volatile boolean ownerNameWasPresent;
        private volatile String previousOwnerName;
        private volatile ScheduledFuture<?> chatTask;
        private volatile ScheduledFuture<?> wiredTask;
        private volatile ScheduledFuture<?> durationTask;

        private StressRun(StressScenario scenario) {
            this.scenario = scenario;
        }

        private boolean isRunning() {
            return this.state == RunState.STARTING || this.state == RunState.ACTIVE || this.state == RunState.STOPPING;
        }

        private void fail(String message) {
            this.error = message;
            this.state = RunState.FAILED;
            this.finishedAtEpochMs = System.currentTimeMillis();
        }

        private void decrement(ItemKind kind) {
            switch (kind) {
                case GENERIC -> this.activeItems.updateAndGet(value -> Math.max(0, value - 1));
                case ROLLER -> this.activeRollers.updateAndGet(value -> Math.max(0, value - 1));
                case WIRED_STACK -> this.activeWiredStacks.updateAndGet(value -> Math.max(0, value - 1));
                case AUXILIARY -> {
                    // Auxiliary entities have no public active counter.
                }
            }
        }

        private StressRunSnapshot snapshot() {
            long now = this.finishedAtEpochMs > 0 ? this.finishedAtEpochMs : System.currentTimeMillis();
            Runtime runtime = Runtime.getRuntime();
            long usedBytes = runtime.totalMemory() - runtime.freeMemory();
            long maxBytes = runtime.maxMemory();
            return new StressRunSnapshot(
                    this.runId,
                    this.state.name(),
                    this.scenario.roomId(),
                    this.roomName,
                    this.scenario.bots(),
                    this.scenario.items(),
                    this.scenario.rollers(),
                    this.scenario.wiredStacks(),
                    this.activeBots.get(),
                    this.activeItems.get(),
                    this.activeRollers.get(),
                    this.activeWiredStacks.get(),
                    this.scenario.chatPerSecond(),
                    this.messagesSent.get(),
                    this.scenario.wiredEventsPerSecond(),
                    this.wiredEventsFired.get(),
                    this.wiredEffectsExecuted.get(),
                    this.startedAtEpochMs,
                    Math.max(0L, (now - this.startedAtEpochMs) / 1_000L),
                    this.scenario.durationSeconds(),
                    this.room == null ? 0D : this.room.lastCycleCpuMs,
                    (int) (usedBytes / 1_024L / 1_024L),
                    maxBytes > 0 ? usedBytes * 100D / maxBytes : 0D,
                    this.error);
        }
    }
}
