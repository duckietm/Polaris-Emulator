package polaris.hotel.wired.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.messages.outgoing.rooms.WiredFurniMoveStyleComposer;

/**
 * Sends the capability-gated move-style hint (header 5110) ahead of wired movement packets.
 * Clients that never announced {@link GameClient#WIRED_FEATURE_MOVE_STYLE} receive nothing and
 * keep the historical linear animation; hints for room units also need
 * {@link GameClient#WIRED_FEATURE_TRAJECTORY}.
 */
public final class WiredMoveStyleHelper {
    public static final int STYLE_LINEAR = 0;
    public static final int STYLE_DROP = 6;
    /** Habbo's jump strength: the intensity is the signed strength, -1000 to 1000. */
    public static final int STYLE_JUMP = 7;

    public static final int DROP_INTENSITY = 100;

    public static final int KIND_FURNI = 0;
    public static final int KIND_UNIT = 1;

    // Hints raised while a wired firing collects its movements, sent as one packet per style when
    // the collection finishes instead of one packet per moved furni.
    private static final ThreadLocal<PendingHints> PENDING = new ThreadLocal<>();
    private static final int MAX_IDS_PER_HINT = 1_000;

    private record HintKey(int kind, int style, int intensity, int overshoot) {}

    private static final class PendingHints {
        private Room room;
        private final Map<HintKey, LinkedHashSet<Integer>> idsByHint = new LinkedHashMap<>();
    }

    private WiredMoveStyleHelper() {}

    static void beginCollection() {
        PENDING.set(new PendingHints());
    }

    static void discardCollection() {
        PENDING.remove();
    }

    static void finishCollection() {
        PendingHints pending = PENDING.get();
        PENDING.remove();

        if (pending == null || pending.room == null) {
            return;
        }

        for (Map.Entry<HintKey, LinkedHashSet<Integer>> entry : pending.idsByHint.entrySet()) {
            List<Integer> ids = new ArrayList<>(entry.getValue());

            for (int from = 0; from < ids.size(); from += MAX_IDS_PER_HINT) {
                send(pending.room, ids.subList(from, Math.min(ids.size(), from + MAX_IDS_PER_HINT)), entry.getKey());
            }
        }
    }

    public static void broadcast(Room room, Collection<Integer> itemIds, int style, int intensity) {
        broadcast(room, itemIds, style, intensity, 0);
    }

    /** A furni hint; a non-zero overshoot lets the animation fly that many tiles past the target. */
    public static void broadcast(Room room, Collection<Integer> itemIds, int style, int intensity, int overshoot) {
        queue(room, itemIds, new HintKey(KIND_FURNI, style, intensity, overshoot));
    }

    /** A hint for room units moved in the same firing; batched like the furni ones. */
    public static void broadcastUnits(Room room, Collection<Integer> unitIds, int style, int intensity) {
        queue(room, unitIds, new HintKey(KIND_UNIT, style, intensity, 0));
    }

    /** A room unit hint sent at once, for a movement packet that is itself about to go out. */
    public static void sendUnitsNow(Room room, Collection<Integer> unitIds, int style, int intensity) {
        if (isInert(room, unitIds, style, 0)) {
            return;
        }

        send(room, unitIds, new HintKey(KIND_UNIT, style, intensity, 0));
    }

    private static void queue(Room room, Collection<Integer> ids, HintKey key) {
        if (isInert(room, ids, key.style(), key.overshoot())) {
            return;
        }

        PendingHints pending = PENDING.get();
        if (pending != null
                && WiredMoveCarryHelper.isCollectingMovements()
                && (pending.room == null || pending.room == room)) {
            pending.room = room;
            pending.idsByHint
                    .computeIfAbsent(key, ignored -> new LinkedHashSet<>())
                    .addAll(ids);
            return;
        }

        send(room, ids, key);
    }

    private static boolean isInert(Room room, Collection<Integer> ids, int style, int overshoot) {
        return room == null || ids == null || ids.isEmpty() || (style == STYLE_LINEAR && overshoot == 0);
    }

    private static void send(Room room, Collection<Integer> ids, HintKey key) {
        var message = new WiredFurniMoveStyleComposer(ids, key.style(), key.intensity(), key.overshoot(), key.kind())
                .compose();
        // A furni hint's trailing overshoot is simply not read by an older client.
        int feature =
                (key.kind() == KIND_UNIT) ? GameClient.WIRED_FEATURE_TRAJECTORY : GameClient.WIRED_FEATURE_MOVE_STYLE;

        for (User user : room.getUsers()) {
            if (user == null || user.getClient() == null) {
                continue;
            }
            if (user.getClient().supportsWiredFeature(GameClient.WIRED_FEATURE_PROTOCOL_VERSION, feature)) {
                user.getClient().sendResponse(message);
            }
        }
    }
}
