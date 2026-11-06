package polaris.hotel.rooms;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import polaris.hotel.users.User;
import polaris.messages.outgoing.rooms.RoomQueueStatusMessage;

/**
 * AIR 13 room queue: the waiting line a user joins when a room is full,
 * instead of being bounced with {@code CantConnect(REASON_FULL)}.
 *
 * <p>The official client shows {@code room_queue} while it is in the queue and
 * drives it with {@code RoomQueueStatus} (our 2208) plus {@code ChangeQueue}
 * (3093), which moves between the visitor queue (target 2) and the spectator
 * queue (target 1). Inside a target the queue is split by type: {@code c} for
 * club members, {@code d} for everybody else, and the official
 * {@code RoomQueueWidgetHandler} prefers the club queue when the user has HC.
 *
 * <p>The queue is kept per room next to the room object, the same way the
 * handitem block and conf-invis room state are kept.
 */
public final class RoomVisitorQueueSupport {

    /** roomId -> (userId -> queue target), insertion ordered. */
    private static final Map<Integer, Map<Integer, Integer>> QUEUES = new ConcurrentHashMap<>();

    private RoomVisitorQueueSupport() {}

    /**
     * Puts the user at the end of the room's visitor queue and tells every
     * queued user its new position.
     *
     * @return the user's own position, 1 based.
     */
    public static int enqueue(Room room, User user) {
        return enqueue(room, user, RoomQueueStatusMessage.TARGET_VISITOR);
    }

    public static int enqueue(Room room, User user, int target) {
        if (room == null || user == null) {
            return 0;
        }

        Map<Integer, Integer> queue = QUEUES.computeIfAbsent(room.getId(), ignored -> new LinkedHashMap<>());
        int userId = user.getUserInfo().getId();

        synchronized (queue) {
            queue.remove(userId);
            queue.put(userId, target);
        }

        user.getUserInfo().setRoomQueueId(room.getId());
        broadcastStatus(room);

        return positionOf(room, user);
    }

    /** Moves an already queued user between the visitor and spectator queue. */
    public static boolean changeQueue(Room room, User user, int target) {
        if (room == null || user == null) {
            return false;
        }

        Map<Integer, Integer> queue = QUEUES.get(room.getId());
        if (queue == null) {
            return false;
        }

        int userId = user.getUserInfo().getId();
        synchronized (queue) {
            if (!queue.containsKey(userId)) {
                return false;
            }
            queue.put(userId, target);
        }

        broadcastStatus(room);
        return true;
    }

    /** Removes the user from the queue of {@code room}, if it is in one. */
    public static boolean remove(Room room, User user) {
        if (room == null || user == null) {
            return false;
        }

        Map<Integer, Integer> queue = QUEUES.get(room.getId());
        if (queue == null) {
            return false;
        }

        boolean removed;
        synchronized (queue) {
            removed = queue.remove(user.getUserInfo().getId()) != null;
        }

        if (removed) {
            broadcastStatus(room);
        }

        return removed;
    }

    /** Removes the user from every room queue; used on logout. */
    public static void removeEverywhere(User user) {
        if (user == null) {
            return;
        }

        int userId = user.getUserInfo().getId();
        for (Map.Entry<Integer, Map<Integer, Integer>> entry : QUEUES.entrySet()) {
            Map<Integer, Integer> queue = entry.getValue();
            synchronized (queue) {
                queue.remove(userId);
            }
        }
    }

    public static boolean isQueued(Room room, User user) {
        if (room == null || user == null) {
            return false;
        }

        Map<Integer, Integer> queue = QUEUES.get(room.getId());
        if (queue == null) {
            return false;
        }

        synchronized (queue) {
            return queue.containsKey(user.getUserInfo().getId());
        }
    }

    /** The users waiting for {@code room}, in queue order. */
    public static List<Integer> queuedUserIds(Room room) {
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
     * Pops the user at the front of the queue together with its queue target,
     * or null when the queue is empty. The target says whether it was waiting
     * in the spectator queue, which decides whether {@code YouAreNotSpectator}
     * has to be sent before forwarding it into the room.
     *
     * @return {@code {userId, target}} or null.
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
                int userId = entry.getKey();
                int target = entry.getValue();
                queue.remove(userId);
                return new int[] {userId, target};
            }
        }

        return null;
    }

    /** Pops the user at the front of the queue, or null when it is empty. */
    public static Integer pollNext(Room room) {
        if (room == null) {
            return null;
        }

        Map<Integer, Integer> queue = QUEUES.get(room.getId());
        if (queue == null) {
            return null;
        }

        synchronized (queue) {
            for (Integer userId : queue.keySet()) {
                queue.remove(userId);
                return userId;
            }
        }

        return null;
    }

    public static void clear(Room room) {
        if (room != null) {
            QUEUES.remove(room.getId());
        }
    }

    /** Sends the current position to every user waiting for {@code room}. */
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

        for (Integer userId : order) {
            User queued = polaris.Emulator.getGameEnvironment().getUserManager().getUser(userId);
            if (queued == null || queued.getClient() == null) {
                continue;
            }

            int target = targets.getOrDefault(userId, RoomQueueStatusMessage.TARGET_VISITOR);
            String queueType = queueTypeOf(queued);
            int ahead = 0;

            for (Integer otherId : order) {
                if (otherId.equals(userId)) {
                    break;
                }
                User other =
                        polaris.Emulator.getGameEnvironment().getUserManager().getUser(otherId);
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

    private static int positionOf(Room room, User user) {
        List<Integer> order = queuedUserIds(room);
        int index = order.indexOf(user.getUserInfo().getId());

        return index < 0 ? 0 : index + 1;
    }

    private static String queueTypeOf(User user) {
        return user.getUserStats() != null && user.getUserStats().hasActiveClub()
                ? RoomQueueStatusMessage.QUEUE_TYPE_CLUB
                : RoomQueueStatusMessage.QUEUE_TYPE_NORMAL;
    }
}
