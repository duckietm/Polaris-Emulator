package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.Emulator;
import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.rooms.items.FloorItemUpdateComposer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Base for Phase-2 wired storage chests (config-based v1). A chest is a config-holder furni (it opens
 * a dialog via the {@link InteractionWiredExtra} channel, but is never applied as a stack add-on — the
 * engine only matches specific add-on classes) whose contents live as JSON in its own
 * {@code items.wired_data} ({@link ChestStorage}). The give effects + chest conditions read/mutate the
 * contents through {@link #getContents()} and persist with {@link #persistContents()}.
 */
public abstract class InteractionWiredChest extends InteractionWiredExtra {
    /** Nitro's key-value furni data format. The chest carries its whole configuration in one. */
    private static final int MAP_DATA_FORMAT = 1;

    /** Appearance modes, as the settings dialog names them. */
    public static final int APPEARANCE_WHEN_LOOKED_INTO = 0;

    public static final int APPEARANCE_ALWAYS_OPEN = 1;
    public static final int APPEARANCE_ALWAYS_CLOSED = 2;

    protected ChestStorage contents = new ChestStorage();

    /** Whether {@link #contents} was read from the chest's store yet. */
    private volatile boolean loaded;

    /**
     * Who currently has this chest's window open.
     *
     * <p>The default appearance mode is "open while someone is looking inside", so the chest has to
     * know that someone is. The client says when it opens and when it closes; a player who leaves the
     * room is dropped from here too, because a window cannot outlive the room it was opened in.
     */
    private final Set<Integer> viewers = ConcurrentHashMap.newKeySet();

    /** What the room was last told the chest looks like, so an unchanged chest sends nothing. */
    private String publishedAppearance = "";

    protected InteractionWiredChest(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    protected InteractionWiredChest(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    /**
     * Nothing: a chest keeps its state in {@code wired_chests} and its furni in
     * {@code wired_chest_items} (AIR model), so the generic wired payload stays empty. Writing it
     * empty also clears any pre-migration JSON the first time the furni is saved.
     */
    @Override
    public String getWiredData() {
        return "";
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.loadFromStore();
    }

    /** The chest's kind: {@link ChestStorage#KIND_CURRENCY} for a coin chest, else furni. */
    public int chestKind() {
        return this instanceof InteractionWiredChestFurni ? ChestStorage.KIND_FURNI : ChestStorage.KIND_CURRENCY;
    }

    /**
     * Read the chest from its store, creating its row on first use. A chest that was never placed
     * before simply starts empty and unupgraded.
     */
    public synchronized void loadFromStore() {
        ChestStore store = ChestStores.current();
        ChestStore.ChestRow row = store.loadChest(this.getId());
        boolean starter = this.isStarterChest();

        if (row == null) {
            ChestStorage fresh = ChestStorage.fromStore(null, 0, 0, List.of(), this.chestKind(), starter);
            store.createChest(this.getId(), this.chestKind(), 0, 0, fresh.toSettingsJson());
            this.contents = fresh;
            this.loaded = true;
            return;
        }

        List<ChestFurniStoredItem> furni = new ArrayList<>();
        for (ChestStore.StoredRow stored : store.loadItems(this.getId())) {
            furni.add(ChestFurniStoredItem.fromStoredRow(stored, baseItem(stored.baseItemId())));
        }
        this.contents = ChestStorage.fromStore(
                row.settings(), row.coins(), row.capacityLevel(), furni, this.chestKind(), starter);
        this.loaded = true;
    }

    /**
     * The chest's contents went back to its owner because the item is being deleted ({@link
     * ChestRelease}): it holds nothing any more and must not create its row again.
     */
    synchronized void markReleased() {
        this.contents = ChestStorage.fromStore(null, 0, 0, List.of(), this.chestKind(), this.isStarterChest());
        this.loaded = true;
        this.viewers.clear();
    }

    private static Item baseItem(int baseItemId) {
        GameEnvironment environment = WiredPlatform.gameEnvironment();
        if (environment == null || environment.getItemManager() == null) return null;
        return environment.getItemManager().getItem(baseItemId);
    }

    // --- moving coins and furni (AIR model) ------------------------------------------------------

    private static final AtomicLong SEQUENCE = new AtomicLong();

    /** Orders deposits: oldest first is ascending. */
    static long nextSequence() {
        long now = System.currentTimeMillis() * 1000L;
        return SEQUENCE.updateAndGet(previous -> Math.max(previous + 1, now));
    }

    /** Whether an inventory item may go into a chest: tradeable furni that is no chest or contract. */
    public static boolean canStore(HabboItem item) {
        if (item == null || item.getBaseItem() == null) return false;
        if (item instanceof InteractionWiredChest
                || item instanceof com.eu.habbo.habbohotel.items.interactions.wired.contract.InteractionWiredContract) {
            return false;
        }
        if (com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredWebApiOwnership.isWebApiItem(item))
            return false;
        return item.getBaseItem().allowTrade();
    }

    /**
     * Put items a player has already handed over (taken out of their inventory) into this furni
     * chest. As many as fit go in, in order; the caller gives the rest back.
     *
     * @return the items now in the chest
     */
    public List<HabboItem> depositFurni(int fromUserId, List<HabboItem> items) {
        List<HabboItem> accepted = new ArrayList<>();
        if (items == null || items.isEmpty() || this.chestKind() != ChestStorage.KIND_FURNI) return accepted;

        ChestStorage storage = this.getContents();
        synchronized (storage) {
            int room = storage.getCapacity() - storage.furniItemCount();
            List<HabboItem> fitting = new ArrayList<>();
            for (HabboItem item : items) {
                if (fitting.size() >= room) break;
                if (!canStore(item)) continue;
                // A pending state change must reach the row before the row moves.
                if (item.needsUpdate()) item.run();
                fitting.add(item);
            }
            if (fitting.isEmpty()) return accepted;

            long sequence = nextSequence();
            Set<Integer> moved = new HashSet<>(ChestStores.current()
                    .depositItems(
                            this.getId(),
                            fromUserId,
                            fitting.stream().map(HabboItem::getId).toList(),
                            sequence));
            for (HabboItem item : fitting) {
                if (!moved.contains(item.getId())) continue;

                ChestFurniStoredItem row = ChestFurniStoredItem.fromHabboItem(item, item.getId());
                row.transactionId = sequence;
                storage.addFurniItem(row);
                item.needsUpdate(false);
                accepted.add(item);
            }
        }
        return accepted;
    }

    /**
     * Take furni out of this chest for a player. {@code picker} chooses the rows while the chest is
     * held, so nobody else can pick the same ones.
     *
     * @return every row that left the chest, mapped to the id the item has in the inventory now
     */
    public Map<ChestFurniStoredItem, Integer> withdrawFurni(
            int toUserId, Function<ChestStorage, List<ChestFurniStoredItem>> picker) {
        Map<ChestFurniStoredItem, Integer> delivered = new LinkedHashMap<>();
        ChestStorage storage = this.getContents();
        synchronized (storage) {
            List<ChestFurniStoredItem> picked = picker.apply(storage);
            if (picked == null || picked.isEmpty()) return delivered;

            Map<Integer, Integer> moved = ChestStores.current()
                    .withdrawItems(
                            this.getId(),
                            toUserId,
                            picked.stream().map(row -> row.inventoryId).toList());
            if (moved.isEmpty()) {
                // Nothing left: the database refused, so the rows stay where they were.
                storage.restoreFurniItems(picked);
                return delivered;
            }
            for (ChestFurniStoredItem row : picked) {
                Integer newId = moved.get(row.inventoryId);
                if (newId != null) delivered.put(row, newId);
            }
        }
        return delivered;
    }

    /**
     * Add coins to this coin chest, as many as fit.
     *
     * @return the coins accepted
     */
    public int addCoins(int amount) {
        if (amount <= 0 || this.chestKind() != ChestStorage.KIND_CURRENCY) return 0;

        ChestStorage storage = this.getContents();
        synchronized (storage) {
            int accepted = Math.min(amount, storage.getCapacity() - storage.getCoins());
            if (accepted <= 0 || !ChestStores.current().addCoins(this.getId(), accepted)) return 0;

            storage.add(ChestStorage.KIND_CURRENCY, ChestStorage.COINS_TYPE, accepted);
            return accepted;
        }
    }

    /**
     * Take up to {@code amount} coins out of this coin chest; a negative amount takes them all.
     *
     * @return the coins taken
     */
    public int takeCoins(int amount) {
        ChestStorage storage = this.getContents();
        synchronized (storage) {
            int held = storage.getCoins();
            int taken = amount < 0 ? held : Math.min(amount, held);
            if (taken <= 0 || !ChestStores.current().addCoins(this.getId(), -taken)) return 0;

            return storage.take(ChestStorage.KIND_CURRENCY, ChestStorage.COINS_TYPE, taken);
        }
    }

    /**
     * Redeem credit furni a player handed over into this coin chest. All of them go in, or none
     * (the caller gives them back then).
     *
     * @param coins their combined value
     * @return true when they were redeemed
     */
    public boolean redeemCreditFurni(int fromUserId, List<HabboItem> creditFurni, int coins) {
        if (creditFurni == null || creditFurni.isEmpty() || coins <= 0) return false;
        if (this.chestKind() != ChestStorage.KIND_CURRENCY) return false;

        ChestStorage storage = this.getContents();
        synchronized (storage) {
            if (storage.getCoins() + (long) coins > storage.getCapacity()) return false;
            if (!ChestStores.current()
                    .redeemCreditFurni(
                            this.getId(),
                            fromUserId,
                            creditFurni.stream().map(HabboItem::getId).toList(),
                            coins)) {
                return false;
            }
            storage.add(ChestStorage.KIND_CURRENCY, ChestStorage.COINS_TYPE, coins);
            for (HabboItem item : creditFurni) item.needsUpdate(false);
            return true;
        }
    }

    /**
     * Whether this is the starter chest players are given rather than one they bought.
     *
     * <p>Read off the furni it is, because that is the only thing that distinguishes one: it holds
     * less and it cannot be made bigger, which is the whole point of handing one out.
     */
    /** Whether wired may reach this chest. Off until its owner upgrades it, and then permanent. */
    public boolean answersWired() {
        return this.getContents().isWiredEnabled();
    }

    public boolean isStarterChest() {
        Item base = this.getBaseItem();
        return base != null && base.getName() != null && base.getName().endsWith("_starter");
    }

    /**
     * Payloads persisted before the sprite id was stored per row only carry the internal base item
     * id. The client needs the furnidata sprite id to render icons/names, so backfill it here — the
     * next persist writes it out permanently.
     */
    private static void resolveLegacySpriteIds(ChestStorage contents) {
        GameEnvironment environment = Emulator.getGameEnvironment();
        if (environment == null || environment.getItemManager() == null) return;

        for (ChestFurniStoredItem stored : contents.furniItems()) {
            if (stored.spriteId > 0) continue;

            Item base = environment.getItemManager().getItem(stored.baseItemId);
            if (base != null) stored.spriteId = base.getSpriteId();
        }
    }

    /**
     * The player who owns this chest, if they are online anywhere in the hotel.
     *
     * <p>Notifications go to them wherever they are, which is the point of a chest telling you that
     * somebody donated to it while you were somewhere else.
     */
    public Habbo owner() {
        GameEnvironment environment = Emulator.getGameEnvironment();
        if (environment == null || environment.getHabboManager() == null) return null;

        return environment.getHabboManager().getHabbo(this.getUserId());
    }

    @Override
    public void onPickUp() {
        // The chest keeps what it holds when it is picked up: its coins and furni stay in its store
        // and come back with it when it is placed again (they used to be wiped here).
        this.viewers.clear();
    }

    /**
     * The furni's own animation state.
     *
     * <p>A chest is not a static prop: its sprite has states, and until now nothing drove them, so
     * every chest in every room sat on state zero forever. What the states mean differs by kind, which
     * is why the subclasses decide.
     */
    protected abstract int visualState();

    /** True while at least one player has this chest's window open. */
    protected boolean isBeingLookedInto() {
        return !this.viewers.isEmpty();
    }

    /**
     * Whether the chest should be showing open right now.
     *
     * <p>Both kinds ask this same question; they only differ in what they draw once the answer is
     * yes, which is why the answer lives here and the drawing lives in the subclasses.
     */
    protected boolean showsOpen() {
        return switch (this.getContents().getAppearanceState()) {
            case APPEARANCE_ALWAYS_OPEN -> true;
            case APPEARANCE_ALWAYS_CLOSED -> false;
            default -> this.isBeingLookedInto();
        };
    }

    /**
     * Forget anyone who is no longer in the room.
     *
     * <p>The client says when it closes a chest, but a client that reloads, crashes or is simply an
     * older build never gets to. A viewer who has left the room certainly is not looking inside, and
     * without this the lid would stay up for the rest of the room's life -- which is exactly how a
     * chest ends up permanently open for everybody.
     */
    // Package-private so the leak this guards against can be tested without a live room.
    void pruneViewers(Room room) {
        if (room == null || this.viewers.isEmpty()) return;

        this.viewers.removeIf(id -> room.getHabbo(id) == null);
    }

    /** @return true when this changed the furni's appearance, so the caller knows to tell the room */
    public boolean openFor(Habbo habbo, Room room) {
        if (habbo == null) return false;

        this.pruneViewers(room);
        int before = this.visualState();
        this.viewers.add(habbo.getHabboInfo().getId());
        if (this.visualState() == before) return false;

        this.publishState(room);
        return true;
    }

    /** @return true when this changed the furni's appearance, so the caller knows to tell the room */
    public boolean closeFor(Habbo habbo, Room room) {
        if (habbo == null) return false;

        this.pruneViewers(room);
        int before = this.visualState();
        this.viewers.remove(habbo.getHabboInfo().getId());
        if (this.visualState() == before) return false;

        this.publishState(room);
        return true;
    }

    /**
     * The chest's configuration, as the furni carries it.
     *
     * <p>Sent to everyone in the room with the item itself rather than through a window-only packet,
     * which is how the official client reads a chest: it asks the furni, not the server. That is also
     * what lets the sprite animate, since {@code state} is the key the renderer turns into an
     * animation state.
     */
    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("state", Integer.toString(this.visualState()));
        data.put("chest_name", this.getContents().getName());
        data.put("chest_desc", this.getContents().getDescription());
        data.put("locked", this.getContents().isLocked() ? "1" : "0");
        data.put("auto_lock", this.getContents().isAutoLock() ? "1" : "0");
        data.put("capacity", Integer.toString(this.getContents().getCapacity()));
        data.put("contents_count", Integer.toString(this.storedCount()));
        data.put("capacity_level", Integer.toString(this.capacityLevel()));
        data.put("everyone_can_open", this.getContents().isAccessOpen() ? "1" : "0");
        data.put("everyone_can_donate", this.getContents().isAccessDonate() ? "1" : "0");
        data.put("state_control_mode", Integer.toString(this.getContents().getAppearanceState()));
        data.put("notify_mode", Integer.toString(this.getContents().getNotifyMode()));
        // AIR ChestNotificationSettingsUI reads the switches from the furni too.
        data.put("notification_chest_full", this.getContents().isNotifyFull() ? "1" : "0");
        data.put("notification_donation", this.getContents().isNotifyDonation() ? "1" : "0");
        data.put("notification_someone_withdraws", this.getContents().isNotifyWithdraw() ? "1" : "0");
        data.put("notification_chest_empty", this.getContents().isNotifyEmpty() ? "1" : "0");
        data.put("notification_wired_transaction", this.getContents().isNotifyWired() ? "1" : "0");

        data.put("is_wired_enabled", this.getContents().isWiredEnabled() ? "1" : "0");
        data.put("preview_mode", Integer.toString(this.getContents().getPreviewMode()));
        data.put("preview_amount", Integer.toString(this.getContents().getPreviewAmount()));
        data.put("visuals", this.previewItems());

        serverMessage.appendInt(MAP_DATA_FORMAT + (this.isLimited() ? 256 : 0));
        serverMessage.appendInt(data.size());
        for (Map.Entry<String, String> entry : data.entrySet()) {
            serverMessage.appendString(entry.getKey());
            serverMessage.appendString(entry.getValue());
        }

        // Deliberately not super: InteractionDefault writes a second format header and the legacy
        // string after it, and those two extra fields desynchronise the reader for every item that
        // follows in the room's furniture list -- which draws the room empty. Only the limited block
        // belongs here, exactly as HabboItem writes it.
        if (this.isLimited()) {
            serverMessage.appendInt(this.getLimitedSells());
            serverMessage.appendInt(this.getLimitedStack());
        }
    }

    /**
     * What the chest floats above its lid, in the shape the client reads: {@code isWallItem,typeId}
     * entries joined by semicolons, which is how the official writes it.
     *
     * <p>Empty unless the owner asked for it: what is in a chest is their business until they decide
     * to put it on show. Distinct types only -- four of the same chair above a lid says less than
     * four different things.
     */
    private String previewItems() {
        StringBuilder visuals = new StringBuilder();
        for (ChestFurniStoredItem stored : this.displayedFurni()) {
            if (visuals.length() > 0) visuals.append(';');
            visuals.append("false,").append(stored.wireTypeId());
        }

        return visuals.toString();
    }

    /**
     * The stored furni the chest shows above its lid: none while the preview is off, otherwise the first
     * item of each type, up to the preview amount. The chest item type scanner reads these as the
     * "displayed" items.
     */
    public List<ChestFurniStoredItem> displayedFurni() {
        List<ChestFurniStoredItem> displayed = new ArrayList<>();
        if (this.getContents().getPreviewMode() <= 0) return displayed;

        Set<Integer> seen = new LinkedHashSet<>();
        for (ChestFurniStoredItem stored : this.getContents().furniItems()) {
            if (seen.size() >= this.getContents().getPreviewAmount()) break;
            if (seen.add(stored.wireTypeId())) displayed.add(stored);
        }

        return displayed;
    }

    /** How many upgrades have been bought, which is what the official calls the capacity level. */
    protected int capacityLevel() {
        return this.getContents().getCapacityLevel();
    }

    /** How full the chest is, in whatever unit its kind counts. */
    protected abstract int storedCount();

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {}

    /**
     * A chest is furniture, not a wired box.
     *
     * <p>{@code InteractionWiredExtra} forces walkability on so nobody gets stranded on top of a wired
     * effect, which is right for a box and wrong for a chest: a chest is a thing that stands in the
     * room, and whether it can be walked on is its own {@code can_walk} setting like any other furni.
     * Without this a chest set solid was walked straight over.
     */
    @Override
    public boolean isWalkable() {
        return this.getBaseItem() != null && this.getBaseItem().allowWalk();
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        // Same split as InteractionDefault: the tile decides, this is only a per-unit veto.
        return true;
    }

    @Override
    public boolean hasConfiguration() {
        return true;
    }

    /** The chest's state, read from its store the first time it is needed. */
    public ChestStorage getContents() {
        if (!this.loaded) {
            synchronized (this) {
                if (!this.loaded) this.loadFromStore();
            }
        }
        return this.contents;
    }

    /**
     * Whether the lock stands between this player and the chest.
     *
     * <p>A lock closes a chest to the room, not to the person who owns it. The official window keeps
     * withdrawing available while {@code locked && owner} and greys depositing only for someone who is
     * not the owner, so the owner can always reach their own chest — which is what makes the lock safe
     * to leave on. Everyone else is refused in both directions until it comes off.
     */
    public boolean isLockedFor(Habbo habbo) {
        if (!this.getContents().isLocked()) return false;

        return habbo == null || habbo.getHabboInfo().getId() != this.getUserId();
    }

    /** Set while a settings save is queued, so a burst of changes is written once. */
    private final AtomicBoolean settingsSaveQueued = new AtomicBoolean(false);

    /**
     * Schedule a save of the chest's settings (and rolling log) to {@code wired_chests}. Coins and
     * furni are not part of it: they are stored the moment they move.
     *
     * <p>Coalesced: the queued save serialises the chest as it is when the save runs, so however many
     * changes pile up before then, the last state is the one written.
     */
    public void persistContents() {
        if (!this.settingsSaveQueued.compareAndSet(false, true)) return;

        Runnable save = () -> {
            this.settingsSaveQueued.set(false);
            ChestStorage storage = this.getContents();
            ChestStores.current().saveSettings(this.getId(), storage.getCapacityLevel(), storage.toSettingsJson());
        };
        com.eu.habbo.threading.ThreadPooling threading = WiredPlatform.threading();
        if (threading == null) {
            save.run();
        } else {
            threading.runPersistence(save);
        }
    }

    /**
     * Save, and tell the room the furni looks different if it does.
     *
     * <p>The room is handed in rather than looked up: a chest knows which room it is in by id, but
     * turning that id back into a room means reaching for the global environment, and everything that
     * changes a chest is already holding the room.
     */
    public void persistContents(Room room) {
        this.persistContents();
        this.publishState(room);
    }

    /**
     * Push the current appearance to the room, but only when it actually changed — a furni update is
     * a packet to everyone standing there.
     */
    public void publishState(Room room) {
        this.pruneViewers(room);

        // Not just the state: a chest showing what it holds changes appearance when its contents
        // change, and a deposit that leaves the lid where it was would otherwise never reach the room.
        String appearance = this.visualState() + "|" + this.previewItems();
        if (room == null || appearance.equals(this.publishedAppearance)) return;

        this.publishedAppearance = appearance;

        // Not updateItemState: that broadcasts ItemStateComposer, which reads the legacy extradata
        // string. A chest keeps its state in its furni data, so that composer would send zero every
        // time and slam the lid shut whatever the chest was actually doing. The full item update
        // re-serializes the furni data, state included.
        room.sendComposer(new FloorItemUpdateComposer(this).compose());
    }

    /**
     * Push the chest's furni data to the room whether or not its look changed: the official client
     * reads a chest's settings, lock and capacity from the furni (AIR model), so a settings change
     * has to reach everyone who might open it.
     */
    public void publishSettings(Room room) {
        if (room == null) return;

        this.pruneViewers(room);
        this.publishedAppearance = this.visualState() + "|" + this.previewItems();
        room.sendComposer(new FloorItemUpdateComposer(this).compose());
    }

    /** The players looking inside right now. */
    public Set<Integer> viewerIds() {
        return Set.copyOf(this.viewers);
    }

    /**
     * Tell everyone looking inside what changed (AIR {@code ChestCoins} with {@code isUpdate} for a
     * coin chest, {@code ChestFurniUpdate} for a furni chest).
     */
    public void notifyViewers(Room room, List<Integer> removedIds, List<ChestFurniStoredItem> added) {
        if (room == null) return;

        this.pruneViewers(room);
        for (int viewerId : this.viewerIds()) {
            Habbo viewer = room.getHabbo(viewerId);
            if (viewer == null || viewer.getClient() == null) continue;

            if (this.chestKind() == ChestStorage.KIND_CURRENCY) {
                viewer.getClient()
                        .sendResponse(new com.eu.habbo.messages.outgoing.rooms.items.ChestCoinsComposer(
                                this.getId(), this.getContents().getCoins(), true));
            } else {
                ChestFurniPackets.sendDelta(viewer.getClient(), this.getId(), removedIds, added);
            }
        }
    }
}
