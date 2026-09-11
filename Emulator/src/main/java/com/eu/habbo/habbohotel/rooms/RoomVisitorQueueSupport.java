package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.rooms.RoomQueueStatusMessage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AIR 13 room queue: the waiting line a habbo joins when a room is full,
 * instead of being bounced with {@code CantConnect(REASON_FULL)}.
 *
 * <p>The official client shows {@code room_queue} while it is in the queue and
 * drives it with {@code RoomQueueStatus} (our 2208) plus {@code ChangeQueue}
 * (3093), which moves between the visitor queue (target 2) and the spectator
 * queue (target 1). Inside a target the queue is split by type: {@code c} for
 * club members, {@code d} for everybody else, and the official
 * {@code RoomQueueWidgetHandler} prefers the club queue when the habbo has HC.
 *
 * <p>The queue is kept per room next to the room object, the same way the
 * handitem block and conf-invis room state are kept.
 */
public final class RoomVisitorQueueSupport {

    /** roomId -> (habboId -> queue target), insertion ordered. */
    private static final Map<Integer, Map<Integer, Integer>> QUEUES = new ConcurrentHashMap<>();

    private RoomVisitorQueueSupport() {}

    /**
     * Puts the habbo at the end of the room's visitor queue and tells every
     * queued habbo its new position.
     *
     * @return the habbo's own position, 1 based.
     */
    public static int enqueue(Room room, Habbo habbo) {
        return enqueue(room, habbo, RoomQueueStatusMessage.TARGET_VISITOR);
    }

    public static int enqueue(Room room, Habbo habbo, int target) {
        if (room == null || habbo == null) {
            return 0;
        }

        Map<Integer, Integer> queue = QUEUES.computeIfAbsent(room.getId(), ignored -> new LinkedHashMap<>());
        int habboId = habbo.getHabboInfo().getId();

        synchronized (queue) {
            queue.remove(habboId);
            queue.put(habboId, target);
        }

        habbo.getHabboInfo().setRoomQueueId(room.getId());
        broadcastStatus(room);

        return positionOf(room, habbo);
    }

    /** Moves an already queued habbo between the visitor and spectator queue. */
    public static boolean changeQueue(Room room, Habbo habbo, int target) {
        if (room == null || habbo == null) {
            return false;
        }

        Map<Integer, Integer> queue = QUEUES.get(room.getId());
        if (queue == null) {
            return false;
        }

        int habboId = habbo.getHabboInfo().getId();
        synchronized (queue) {
            if (!queue.containsKey(habboId)) {
                return false;
            }
            queue.put(habboId, target);
        }

        broadcastStatus(room);
        return true;
    }

    /** Removes the habbo from the queue of {@code room}, if it is in one. */
    public static boolean remove(Room room, Habbo habbo) {
        if (room == null || habbo == null) {
            return false;
        }

        Map<Integer, Integer> queue = QUEUES.get(room.getId());
        if (queue == null) {
            return false;
        }

        boolean removed;
        synchronized (queue) {
            removed = queue.remove(habbo.getHabboInfo().getId()) != null;
        }

        if (removed) {
            broadcastStatus(room);
        }

        return removed;
    }

    /** Removes the habbo from every room queue; used on logout. */
    public static void removeEverywhere(Habbo habbo) {
        if (habbo == null) {
            return;
        }

        int habboId = habbo.getHabboInfo().getId();
        for (Map.Entry<Integer, Map<Integer, Integer>> entry : QUEUES.entrySet()) {
            Map<Integer, Integer> queue = entry.getValue();
            synchronized (queue) {
                queue.remove(habboId);
            }
        }
    }

    public static boolean isQueued(Room room, Habbo habbo) {
        if (room == null || habbo == null) {
            return false;
        }

        Map<Integer, Integer> queue = QUEUES.get(room.getId());
        if (queue == null) {
            return false;
        }

        synchronized (queue) {
            return queue.containsKey(habbo.getHabboInfo().getId());
        }
    }

    /** The habbos waiting for {@code room}, in queue order. */
    public static List<Integer> queuedHabboIds(Room room) {
        if (room == null) {
            return new ArrayList<>();
        }

        Map<Integer, Integer> queue = QUEUES.get(room.getId());
        if (queue == null) {
            return new ArrayList<>();
        }

        synchronized (queue) {
            return new ArrayList<>(queue.keySet());
        }
    }

    /**
     * Pops the habbo at the front of the queue together with its queue target,
     * or null when the queue is empty. The target says whether it was waiting
     * in the spectator queue, which decides whether {@code YouAreNotSpectator}
     * has to be sent before forwarding it into the room.
     *
     * @return {@code {habboId, target}} or null.
     */
    public static int[] pollNextQueued(Room room) {
        if (room == null) {
            return null;
        }

        Map<Integer, Integer> queue = QUEUES.get(room.getId());
        if (queue == null) {
            return null;
        }

        synchronized (queue) {
            for (Map.Entry<Integer, Integer> entry : queue.entrySet()) {
                int habboId = entry.getKey();
                int target = entry.getValue();
                queue.remove(habboId);
                return new int[] {habboId, target};
            }
        }

        return null;
    }

    /** Pops the habbo at the front of the queue, or null when it is empty. */
    public static Integer pollNext(Room room) {
        if (room == null) {
            return null;
        }

        Map<Integer, Integer> queue = QUEUES.get(room.getId());
        if (queue == null) {
            return null;
        }

        synchronized (queue) {
            for (Integer habboId : queue.keySet()) {
                queue.remove(habboId);
                return habboId;
            }
        }

        return null;
    }

    public static void clear(Room room) {
        if (room != null) {
            QUEUES.remove(room.getId());
        }
    }

    /** Sends the current position to every habbo waiting for {@code room}. */
    public static void broadcastStatus(Room room) {
        if (room == null) {
            return;
        }

        Map<Integer, Integer> queue = QUEUES.get(room.getId());
        if (queue == null) {
            return;
        }

        List<Integer> order;
        Map<Integer, Integer> targets;
        synchronized (queue) {
            order = new ArrayList<>(queue.keySet());
            targets = new LinkedHashMap<>(queue);
        }

        for (Integer habboId : order) {
            Habbo queued =
                    com.eu.habbo.Emulator.getGameEnvironment().getHabboManager().getHabbo(habboId);
            if (queued == null || queued.getClient() == null) {
                continue;
            }

            int target = targets.getOrDefault(habboId, RoomQueueStatusMessage.TARGET_VISITOR);
            String queueType = queueTypeOf(queued);
            int ahead = 0;

            for (Integer otherId : order) {
                if (otherId.equals(habboId)) {
                    break;
                }
                Habbo other = com.eu.habbo.Emulator.getGameEnvironment()
                        .getHabboManager()
                        .getHabbo(otherId);
                if (other == null) {
                    continue;
                }
                if (targets.getOrDefault(otherId, RoomQueueStatusMessage.TARGET_VISITOR) != target) {
                    continue;
                }
                if (!queueTypeOf(other).equals(queueType)) {
                    continue;
                }
                ahead++;
            }

            queued.getClient().sendResponse(RoomQueueStatusMessage.forPosition(room.getId(), target, queueType, ahead));
        }
    }

    private static int positionOf(Room room, Habbo habbo) {
        List<Integer> order = queuedHabboIds(room);
        int index = order.indexOf(habbo.getHabboInfo().getId());

        return index < 0 ? 0 : index + 1;
    }

    private static String queueTypeOf(Habbo habbo) {
        return habbo.getHabboStats() != null && habbo.getHabboStats().hasActiveClub()
                ? RoomQueueStatusMessage.QUEUE_TYPE_CLUB
                : RoomQueueStatusMessage.QUEUE_TYPE_NORMAL;
    }
}
