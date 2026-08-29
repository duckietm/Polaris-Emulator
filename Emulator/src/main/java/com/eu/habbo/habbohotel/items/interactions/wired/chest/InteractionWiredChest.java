package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.rooms.items.FloorItemUpdateComposer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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

    @Override
    public String getWiredData() {
        return this.contents.toJson();
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.contents = ChestStorage.fromJson(set.getString("wired_data"));
        resolveLegacySpriteIds(this.contents);

        if (this.isStarterChest()) this.contents.applyStarterCeiling();
    }

    /**
     * Whether this is the starter chest players are given rather than one they bought.
     *
     * <p>Read off the furni it is, because that is the only thing that distinguishes one: it holds
     * less and it cannot be made bigger, which is the whole point of handing one out.
     */
    /** Whether wired may reach this chest. Off until its owner upgrades it, and then permanent. */
    public boolean answersWired() {
        return this.contents.isWiredEnabled();
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
        this.contents = new ChestStorage();
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
        return switch (this.contents.getAppearanceState()) {
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
        data.put("chest_name", this.contents.getName());
        data.put("chest_desc", this.contents.getDescription());
        data.put("locked", this.contents.isLocked() ? "1" : "0");
        data.put("auto_lock", this.contents.isAutoLock() ? "1" : "0");
        data.put("capacity", Integer.toString(this.contents.getCapacity()));
        data.put("contents_count", Integer.toString(this.storedCount()));
        data.put("capacity_level", Integer.toString(this.capacityLevel()));
        data.put("everyone_can_open", this.contents.isAccessOpen() ? "1" : "0");
        data.put("everyone_can_donate", this.contents.isAccessDonate() ? "1" : "0");
        data.put("state_control_mode", Integer.toString(this.contents.getAppearanceState()));
        data.put("notify_mode", Integer.toString(this.contents.getNotifyMode()));

        data.put("is_wired_enabled", this.contents.isWiredEnabled() ? "1" : "0");
        data.put("preview_mode", Integer.toString(this.contents.getPreviewMode()));
        data.put("preview_amount", Integer.toString(this.contents.getPreviewAmount()));
        data.put("preview_items", this.previewItems());

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
     * The furnidata ids the chest shows on top of itself, newest first, comma separated.
     *
     * <p>Empty unless the owner asked for a preview: what is in a chest is the owner's business until
     * they decide to put it on show. Distinct types only -- four of the same chair on the lid says
     * less than four different things.
     */
    private String previewItems() {
        if (this.contents.getPreviewMode() <= 0) return "";

        StringBuilder ids = new StringBuilder();
        Set<Integer> seen = new LinkedHashSet<>();

        for (ChestFurniStoredItem stored : this.contents.furniItems()) {
            if (seen.size() >= this.contents.getPreviewAmount()) break;
            if (!seen.add(stored.wireTypeId())) continue;

            if (ids.length() > 0) ids.append(',');
            ids.append(stored.wireTypeId());
        }

        return ids.toString();
    }

    /** How many upgrades have been bought, which is what the official calls the capacity level. */
    protected int capacityLevel() {
        int bought = this.contents.getCapacityMax() - ChestStorage.DEFAULT_CAPACITY;
        return bought <= 0 ? 0 : bought / ChestStorage.CAPACITY_STEP;
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

    public ChestStorage getContents() {
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
        if (!this.contents.isLocked()) return false;

        return habbo == null || habbo.getHabboInfo().getId() != this.getUserId();
    }

    /** Schedule a save of the (mutated) contents to items.wired_data via {@code InteractionWired.run()}. */
    public void persistContents() {
        this.needsUpdate(true);
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
}
