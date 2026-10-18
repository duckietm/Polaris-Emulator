package com.eu.habbo.habbohotel.wired.leaderboard;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraUserVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableLeaderboard;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.tick.WiredTickable;
import com.eu.habbo.messages.outgoing.wired.WiredLeaderboardChunkComposer;
import com.eu.habbo.messages.outgoing.wired.WiredLeaderboardSnapshotComposer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The leaderboard panels of one room, as the official client expects them: a snapshot of every
 * panel when a player enters and whenever a panel changes, and the chunks of 50 rows each player
 * subscribed to, sent again whenever their rows or the total change.
 *
 * <p>As in Habbo, row updates go out on a fixed grid of {@link #PUSH_PERIOD_MS} (writes at +0.3 s and
 * +12.3 s reach the players at +10 s and +20 s), while a changed panel and the first chunks of a new
 * subscription go out at once. On each grid step chunks are only read again when the room's user
 * variables changed (or a panel did), and a slow refresh catches what that misses (a renamed user, a
 * new figure, a changed level-up box). Each chunk is read once per pass however many players look at
 * it, and only what differs from what a player was last sent goes out. Everything runs under this
 * service's lock: the tick and the subscribe requests both use it.
 *
 * <p>Panels are numbered from 1 in the order of their boxes, and every revision comes from one counter
 * for the whole room, as the official server numbers them.
 */
public final class WiredLeaderboardService implements WiredTickable {
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredLeaderboardService.class);

    /** The tick service's ids are furni ids; this is not one, so it uses a seat no furni has. */
    private static final int SERVICE_ID = -8_000_000;
    /** How often the panels are looked at. */
    private static final int FLUSH_MS = 500;
    /** The grid row updates go out on. */
    static final long PUSH_PERIOD_MS = 10_000;
    /** How often every subscribed chunk is read again even when no variable changed. */
    private static final int REFRESH_MS = 10_000;
    /** Panels per room; the client shows at most four. */
    static final int MAX_OVERLAYS = 4;
    /** Variables per panel; the client shows at most ten. */
    static final int MAX_CHOICES = 10;
    /** Chunks one subscription may name, and the highest index accepted. */
    static final int MAX_SUBSCRIBED_CHUNKS = 2;

    static final int MAX_CHUNK_INDEX = WiredLeaderboardQuery.MAX_FILTERED_HOLDERS;

    private final int roomId;
    private final Map<Integer, Revision> revisions = new HashMap<>();
    private final Map<Integer, Map<Integer, Subscription>> subscriptions = new HashMap<>();
    private List<Panel> panels = List.of();
    private String snapshotSignature = "";
    private long changeVersion = -1;
    private long lastRefreshMs;
    private long lastPushSlot = -1;
    private int roomRevision;

    public WiredLeaderboardService(int roomId) {
        this.roomId = roomId;
    }

    @Override
    public void onWiredTick(Room room, long tickCount, int tickIntervalMs) {
        int ticksPerFlush = Math.max(1, FLUSH_MS / Math.max(1, tickIntervalMs));
        if (tickCount % ticksPerFlush != 0) return;

        try {
            this.flush(room);
        } catch (Exception e) {
            LOGGER.error("Wired leaderboard flush failed in room {}", this.roomId, e);
        }
    }

    @Override
    public void resetTimer() {}

    @Override
    public int getId() {
        return SERVICE_ID - this.roomId;
    }

    @Override
    public int getRoomId() {
        return this.roomId;
    }

    /** One pass: the panels, then the chunks that changed. */
    synchronized void flush(Room room) {
        if (room == null) return;

        boolean panelsChanged = this.refreshPanels(room);
        if (panelsChanged) {
            WiredLeaderboardSnapshotComposer snapshot = new WiredLeaderboardSnapshotComposer(this.descriptors());
            for (Habbo habbo : room.getHabbos()) {
                if (habbo != null && habbo.getClient() != null)
                    habbo.getClient().sendResponse(snapshot);
            }
        }

        this.dropStaleSubscriptions(room);
        if (this.panels.isEmpty() && this.subscriptions.isEmpty()) {
            WiredLeaderboardSupport.release(room, this);
            return;
        }

        long now = System.currentTimeMillis();
        long slot = pushSlot(now);
        // Row updates wait for the next step of the grid; a changed panel goes out at once.
        if (!panelsChanged && slot == this.lastPushSlot) return;

        long version = room.getUserVariableManager().getChangeVersion();
        boolean refresh = now - this.lastRefreshMs >= REFRESH_MS;
        if (!panelsChanged && !refresh && version == this.changeVersion) return;

        this.lastPushSlot = slot;
        this.changeVersion = version;
        if (refresh) this.lastRefreshMs = now;
        this.pushChunks(room);
    }

    /** The step of the push grid a time falls in. */
    static long pushSlot(long nowMs) {
        return Math.floorDiv(nowMs, PUSH_PERIOD_MS);
    }

    /** A player entered: they start without subscriptions and get every panel. */
    synchronized void hello(Room room, Habbo habbo) {
        this.refreshPanels(room);
        this.subscriptions.remove(habbo.getHabboInfo().getId());
        habbo.getClient().sendResponse(new WiredLeaderboardSnapshotComposer(this.descriptors()));
    }

    /**
     * A player's chunks of one panel, replacing what they had for it. A request for a panel that is
     * gone, an old revision or a variable the panel does not offer is dropped, as the official server
     * does; the client asks again once it has the new snapshot.
     */
    synchronized void subscribe(
            Room room, Habbo habbo, int overlayId, int revision, String variableId, int[] chunkIndices) {
        if (habbo.getClient() == null || !isValidChunkSet(chunkIndices)) return;

        Panel panel = this.panel(overlayId);
        if (panel == null
                || panel.descriptor().revision() != revision
                || !panel.descriptor().offers(variableId)) {
            return;
        }

        int userId = habbo.getHabboInfo().getId();
        Map<Integer, Subscription> current = this.subscriptions.get(userId);
        Subscription previous = (current != null) ? current.get(overlayId) : null;
        if (previous != null
                && previous.revision() == revision
                && previous.variableId().equals(variableId)
                && Arrays.equals(previous.chunks(), chunkIndices)) {
            return;
        }

        Subscription subscription = new Subscription(revision, variableId, chunkIndices.clone());
        this.subscriptions.computeIfAbsent(userId, id -> new HashMap<>()).put(overlayId, subscription);

        for (int chunkIndex : subscription.chunks()) {
            WiredLeaderboardChunk chunk = WiredLeaderboardQuery.chunk(room, panel.config(), variableId, chunkIndex);
            subscription.sent().put(chunkIndex, chunk.toString());
            habbo.getClient().sendResponse(new WiredLeaderboardChunkComposer(overlayId, revision, variableId, chunk));
        }
    }

    /** Chunk sets the client sends: one or two distinct indices, ascending. */
    static boolean isValidChunkSet(int[] chunkIndices) {
        if (chunkIndices == null || chunkIndices.length == 0 || chunkIndices.length > MAX_SUBSCRIBED_CHUNKS) {
            return false;
        }
        for (int i = 0; i < chunkIndices.length; i++) {
            if (chunkIndices[i] < 0 || chunkIndices[i] > MAX_CHUNK_INDEX) return false;
            if (i > 0 && chunkIndices[i] <= chunkIndices[i - 1]) return false;
        }
        return true;
    }

    /** Reads the room's panels again; true when what the client is told about them changed. */
    private boolean refreshPanels(Room room) {
        List<Panel> next = new ArrayList<>();
        for (WiredExtraVariableLeaderboard box : boxes(room)) {
            int overlayId = next.size() + 1;
            WiredLeaderboardConfig config = box.getConfig();
            List<WiredLeaderboardDescriptor.Choice> choices = new ArrayList<>();
            for (WiredExtraUserVariable variable : box.getCandidates(room)) {
                if (choices.size() >= MAX_CHOICES) break;
                String id = Integer.toString(variable.getId());
                String name = config.displayOverrides().get(id);
                choices.add(new WiredLeaderboardDescriptor.Choice(
                        id, name != null ? name : WiredLeaderboardCodec.suggest(variable.getVariableName())));
            }
            String defaultVariableId = choices.isEmpty()
                    ? WiredLeaderboardCodec.NULL_VARIABLE_ID
                    : choices.get(0).variableId();
            int revision = this.revisionOf(overlayId, box.getId(), semanticKey(config, choices));
            next.add(new Panel(
                    config,
                    new WiredLeaderboardDescriptor(
                            overlayId,
                            revision,
                            config.title(),
                            config.description(),
                            config.tileX(),
                            config.tileY(),
                            config.offsetX(),
                            config.altitude(),
                            config.sortMode(),
                            config.columns(),
                            choices,
                            defaultVariableId,
                            config.pageSizeOption())));
        }
        this.panels = List.copyOf(next);
        String signature = this.descriptors().toString();
        if (signature.equals(this.snapshotSignature)) return false;

        this.snapshotSignature = signature;
        return true;
    }

    /**
     * The panel's revision, taken from the room's counter when what it ranks (sort, filter, variables)
     * changed or another box now shows as this panel, so a client never keeps an older panel's rows.
     */
    private int revisionOf(int overlayId, int boxId, String semanticKey) {
        Revision known = this.revisions.get(overlayId);
        if (known != null && known.boxId() == boxId && known.semanticKey().equals(semanticKey)) {
            return known.revision();
        }

        int revision = ++this.roomRevision;
        this.revisions.put(overlayId, new Revision(boxId, semanticKey, revision));
        return revision;
    }

    static String semanticKey(WiredLeaderboardConfig config, List<WiredLeaderboardDescriptor.Choice> choices) {
        StringBuilder key = new StringBuilder();
        key.append(config.sortMode())
                .append('|')
                .append(config.filterEnabled())
                .append('|')
                .append(config.compareEnabled())
                .append('|')
                .append(config.operandKind())
                .append('|')
                .append(config.filterVariableId())
                .append('|')
                .append(config.operandVariableId())
                .append('|')
                .append(config.comparisonLiteral());
        for (WiredLeaderboardDescriptor.Choice choice : choices) {
            key.append('|').append(choice.variableId());
        }
        return key.toString();
    }

    /** Players who left, and subscriptions to panels that are gone or changed meaning. */
    private void dropStaleSubscriptions(Room room) {
        this.subscriptions.entrySet().removeIf(entry -> {
            if (room.getHabbo(entry.getKey()) == null) return true;

            entry.getValue().entrySet().removeIf(overlay -> {
                Panel panel = this.panel(overlay.getKey());
                return panel == null
                        || panel.descriptor().revision() != overlay.getValue().revision()
                        || !panel.descriptor().offers(overlay.getValue().variableId());
            });
            return entry.getValue().isEmpty();
        });
    }

    /** Every subscribed chunk whose rows or total differ from what its player was last sent. */
    private void pushChunks(Room room) {
        Map<String, WiredLeaderboardChunk> read = new HashMap<>();
        for (Map.Entry<Integer, Map<Integer, Subscription>> entry : this.subscriptions.entrySet()) {
            Habbo habbo = room.getHabbo(entry.getKey());
            if (habbo == null || habbo.getClient() == null) continue;

            for (Map.Entry<Integer, Subscription> overlay : entry.getValue().entrySet()) {
                Panel panel = this.panel(overlay.getKey());
                Subscription subscription = overlay.getValue();
                if (panel == null) continue;

                for (int chunkIndex : subscription.chunks()) {
                    WiredLeaderboardChunk chunk = read.computeIfAbsent(
                            overlay.getKey() + "|" + subscription.variableId() + "|" + chunkIndex,
                            key -> WiredLeaderboardQuery.chunk(
                                    room, panel.config(), subscription.variableId(), chunkIndex));
                    String signature = chunk.toString();
                    if (signature.equals(subscription.sent().get(chunkIndex))) continue;

                    subscription.sent().put(chunkIndex, signature);
                    habbo.getClient()
                            .sendResponse(new WiredLeaderboardChunkComposer(
                                    overlay.getKey(), subscription.revision(), subscription.variableId(), chunk));
                }
            }
        }
    }

    private Panel panel(int overlayId) {
        for (Panel panel : this.panels) {
            if (panel.descriptor().overlayId() == overlayId) return panel;
        }
        return null;
    }

    List<WiredLeaderboardDescriptor> descriptors() {
        List<WiredLeaderboardDescriptor> descriptors = new ArrayList<>(this.panels.size());
        for (Panel panel : this.panels) descriptors.add(panel.descriptor());
        return descriptors;
    }

    /** The panel number a box shows as (from 1), or 0 when it is not shown. */
    static int overlayIdOf(Room room, WiredExtraVariableLeaderboard box) {
        return boxes(room).indexOf(box) + 1;
    }

    /** The room's configured leaderboard boxes, oldest first, at most four. */
    static List<WiredExtraVariableLeaderboard> boxes(Room room) {
        List<WiredExtraVariableLeaderboard> boxes = new ArrayList<>();
        if (room.getRoomSpecialTypes() == null) return boxes;

        for (InteractionWiredExtra extra : room.getRoomSpecialTypes().getExtras()) {
            if (extra instanceof WiredExtraVariableLeaderboard box && box.isPublished()) boxes.add(box);
        }
        boxes.sort(Comparator.comparingInt(WiredExtraVariableLeaderboard::getId));
        return boxes.size() > MAX_OVERLAYS ? new ArrayList<>(boxes.subList(0, MAX_OVERLAYS)) : boxes;
    }

    private record Panel(WiredLeaderboardConfig config, WiredLeaderboardDescriptor descriptor) {}

    private record Revision(int boxId, String semanticKey, int revision) {}

    private record Subscription(int revision, String variableId, int[] chunks, Map<Integer, String> sent) {
        Subscription(int revision, String variableId, int[] chunks) {
            this(revision, variableId, chunks, new LinkedHashMap<>());
        }

        @Override
        public String toString() {
            return this.variableId + Arrays.toString(this.chunks);
        }
    }
}
