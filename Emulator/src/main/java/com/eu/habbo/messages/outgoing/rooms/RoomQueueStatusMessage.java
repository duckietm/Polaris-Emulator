package com.eu.habbo.messages.outgoing.rooms;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AIR 13 {@code RoomQueueStatus} (official id 383, ours 2208 because 383 is
 * taken by the wired trigger data in both repos; parser {@code class_2993}).
 *
 * <p>Wire: {@code int flatId}, then a list of queue sets
 * {@code str name, int target, int queueCount, [ str queueType, int size ]}.
 * The official {@code RoomQueueWidgetHandler} shows {@code size + 1} as the
 * position, so {@code size} is the number of habbos ahead in that queue type.
 * Targets are 1 = spectator queue and 2 = visitor queue; queue types are
 * {@code c} (club) and {@code d} (normal).
 */
public class RoomQueueStatusMessage extends MessageComposer {

    public static final int TARGET_SPECTATOR = 1;
    public static final int TARGET_VISITOR = 2;

    public static final String QUEUE_TYPE_CLUB = "c";
    public static final String QUEUE_TYPE_NORMAL = "d";

    private final int flatId;
    private final String queueSetName;
    private final int target;
    private final Map<String, Integer> queues;

    /**
     * @deprecated kept for the plugin ABI; sends an empty status for room 0.
     */
    @Deprecated
    public RoomQueueStatusMessage() {
        this(0, "", TARGET_VISITOR, new LinkedHashMap<>());
    }

    public RoomQueueStatusMessage(int flatId, String queueSetName, int target, Map<String, Integer> queues) {
        this.flatId = flatId;
        this.queueSetName = queueSetName;
        this.target = target;
        this.queues = queues;
    }

    public static RoomQueueStatusMessage forPosition(int flatId, int target, String queueType, int aheadCount) {
        Map<String, Integer> queues = new LinkedHashMap<>();
        queues.put(queueType, aheadCount);

        return new RoomQueueStatusMessage(
                flatId, target == TARGET_SPECTATOR ? "spectators" : "visitors", target, queues);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomQueueStatusMessage);

        this.response.appendInt(this.flatId);
        this.response.appendInt(1);
        this.response.appendString(this.queueSetName);
        this.response.appendInt(this.target);
        this.response.appendInt(this.queues.size());
        for (Map.Entry<String, Integer> queue : this.queues.entrySet()) {
            this.response.appendString(queue.getKey());
            this.response.appendInt(queue.getValue());
        }

        return this.response;
    }

    public int getFlatId() {
        return this.flatId;
    }

    public int getTarget() {
        return this.target;
    }

    public Map<String, Integer> getQueues() {
        return this.queues;
    }
}
