package com.eu.habbo.habbohotel.wired.link;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.InteractionWiredLink;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.IntFunction;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Carries link messages and dataframes between WIRED Links. AIR has no packet for this part, so the
 * transport is ours, with limits so a Link cannot be used to flood other rooms:
 *
 * <ul>
 *   <li>Both ends must still be a Link owned by the network's owner, standing in a room that owner
 *       controls (owner or rights). A room is only ever reached through a Link its owner's network
 *       put there.
 *   <li>Only loaded rooms receive; nothing loads a room.
 *   <li>Every delivery runs on the thread pool, never inside the sending room's execution, so two
 *       rooms never wait on each other.
 *   <li>A message carries its hop count; a stack started by a received message that sends again adds
 *       one, and past {@code wired.link.max_hops} (default 3) nothing is sent, so rooms cannot ping-pong
 *       forever.
 *   <li>A room sends at most {@code wired.link.sends_per_second} (default 10) and receives at most
 *       {@code wired.link.receives_per_second} (default 20) link messages per second; the rest are
 *       dropped.
 * </ul>
 */
public final class WiredLinkTransport {
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredLinkTransport.class);

    public static final String MAX_HOPS_SETTING = "wired.link.max_hops";
    public static final String SENDS_PER_SECOND_SETTING = "wired.link.sends_per_second";
    public static final String RECEIVES_PER_SECOND_SETTING = "wired.link.receives_per_second";
    /** AIR lets a receive-and-resend chain run 10 times in all. */
    public static final int DEFAULT_MAX_HOPS = 10;
    /** High enough that a 10-run chain over several Links is not cut by the per-second limits. */
    public static final int DEFAULT_SENDS_PER_SECOND = 50;

    public static final int DEFAULT_RECEIVES_PER_SECOND = 100;
    /** The most Links one send reaches. */
    public static final int MAX_RECEIVERS = 50;

    private static final WiredLinkTransport INSTANCE = new WiredLinkTransport(
            WiredLinkService.get(),
            WiredLinkTransport::loadedRoom,
            WiredLinkTransport::runOnPool,
            System::currentTimeMillis,
            WiredLinkTransport::dispatch);

    /** Hands a receive event to the trigger's stack. */
    @FunctionalInterface
    public interface Dispatcher {
        void dispatch(Room room, InteractionWiredTrigger trigger, WiredEvent event);
    }

    private final WiredLinkService service;
    private final IntFunction<Room> rooms;
    private final Executor executor;
    private final LongSupplier clock;
    private final Dispatcher dispatcher;
    private final Map<Integer, Window> sends = new ConcurrentHashMap<>();
    private final Map<Integer, Window> receives = new ConcurrentHashMap<>();

    public WiredLinkTransport(
            WiredLinkService service,
            IntFunction<Room> rooms,
            Executor executor,
            LongSupplier clock,
            Dispatcher dispatcher) {
        this.service = service;
        this.rooms = rooms;
        this.executor = executor;
        this.clock = clock;
        this.dispatcher = dispatcher;
    }

    public static WiredLinkTransport get() {
        return INSTANCE;
    }

    /**
     * Sends the payload through the Link standing in the room. {@code hops} is the hop count of the
     * event the sending stack runs for (0 when it did not come over a Link). Answers how many Links it
     * went to.
     */
    public int send(Room room, InteractionWiredLink link, WiredLinkPayload payload, int hops) {
        if (room == null || link == null || payload == null || link.getRoomId() != room.getId()) {
            return 0;
        }
        if (hops >= maxHops()) {
            LOGGER.debug("[WiredLink] hop limit reached at Link {} in room {}", link.getId(), room.getId());
            return 0;
        }
        int ownerId = link.getUserId();
        if (!controls(room, ownerId)) {
            return 0;
        }
        List<WiredLinkEndpoint> receivers = this.service.receivers(ownerId, link.getId());
        if (receivers.isEmpty()) {
            return 0;
        }
        if (!acquire(this.sends, room.getId(), setting(SENDS_PER_SECOND_SETTING, DEFAULT_SENDS_PER_SECOND))) {
            LOGGER.debug("[WiredLink] room {} is over its send limit", room.getId());
            return 0;
        }
        link.pulse(room);

        int next = hops + 1;
        List<WiredLinkEndpoint> targets =
                receivers.size() > MAX_RECEIVERS ? new ArrayList<>(receivers.subList(0, MAX_RECEIVERS)) : receivers;
        for (WiredLinkEndpoint target : targets) {
            int sourceRoomId = room.getId();
            this.executor.execute(() -> this.deliver(target, payload, next, sourceRoomId));
        }
        return targets.size();
    }

    /** Delivers to one Link: it must still be there, still its owner's, in a room the owner controls. */
    void deliver(WiredLinkEndpoint target, WiredLinkPayload payload, int hops) {
        this.deliver(target, payload, hops, 0);
    }

    /** As {@link #deliver(WiredLinkEndpoint, WiredLinkPayload, int)}, from the room the payload was sent in. */
    void deliver(WiredLinkEndpoint target, WiredLinkPayload payload, int hops, int sourceRoomId) {
        try {
            Room room = this.rooms.apply(target.roomId());
            if (room == null || room.getRoomSpecialTypes() == null) {
                return;
            }
            HabboItem item = room.getHabboItem(target.itemId());
            if (!(item instanceof InteractionWiredLink link)
                    || item.getUserId() != target.ownerId()
                    || !controls(room, target.ownerId())) {
                return;
            }
            if (!acquire(
                    this.receives, room.getId(), setting(RECEIVES_PER_SECOND_SETTING, DEFAULT_RECEIVES_PER_SECOND))) {
                LOGGER.debug("[WiredLink] room {} is over its receive limit", room.getId());
                return;
            }
            link.pulse(room);

            WiredTriggerType type = payload.kind() == WiredLinkPayload.Kind.MESSAGE
                    ? WiredTriggerType.RECEIVE_LINK_MESSAGE
                    : WiredTriggerType.RECEIVE_LINK_DATAFRAME;
            for (InteractionWiredTrigger trigger :
                    new ArrayList<>(room.getRoomSpecialTypes().getTriggers(type))) {
                if (trigger instanceof WiredLinkReceiver receiver
                        && receiver.linkKind() == payload.kind()
                        && receiver.listensTo(link)) {
                    WiredEvent event = receiver.receiveEvent(room, link, payload, hops, sourceRoomId);
                    if (event != null) {
                        this.dispatcher.dispatch(room, trigger, event);
                    }
                }
            }
        } catch (RuntimeException e) {
            LOGGER.error("[WiredLink] delivery to Link {} failed", target.itemId(), e);
        }
    }

    /** The room's owner, or a user with rights in it. */
    static boolean controls(Room room, int userId) {
        if (room == null || userId <= 0) return false;
        if (room.getOwnerId() == userId) return true;
        return room.getRightsManager() != null && room.getRightsManager().hasRights(userId);
    }

    /** A one-second window counter per room. */
    private boolean acquire(Map<Integer, Window> windows, int roomId, int perSecond) {
        long now = this.clock.getAsLong();
        Window window = windows.computeIfAbsent(roomId, id -> new Window());
        synchronized (window) {
            if (now - window.start >= 1000L) {
                window.start = now;
                window.count = 0;
            }
            if (window.count >= perSecond) {
                return false;
            }
            window.count++;
            return true;
        }
    }

    /** Forgets the room's counters (it unloaded). */
    public void forgetRoom(int roomId) {
        this.sends.remove(roomId);
        this.receives.remove(roomId);
    }

    static int maxHops() {
        return Math.max(1, Math.min(10, setting(MAX_HOPS_SETTING, DEFAULT_MAX_HOPS)));
    }

    private static int setting(String key, int fallback) {
        ConfigurationManager config = WiredPlatform.configuration();
        int value = config == null ? fallback : config.getInt(key, fallback);
        return Math.max(1, Math.min(1000, value));
    }

    private static Room loadedRoom(int roomId) {
        if (WiredPlatform.gameEnvironment() == null
                || WiredPlatform.gameEnvironment().getRoomManager() == null) {
            return null;
        }
        Room room = WiredPlatform.gameEnvironment().getRoomManager().getRoom(roomId);
        return room != null && room.isLoaded() ? room : null;
    }

    private static void runOnPool(Runnable task) {
        if (WiredPlatform.threading() != null) {
            WiredPlatform.threading().run(task);
        }
    }

    private static void dispatch(Room room, InteractionWiredTrigger trigger, WiredEvent event) {
        WiredManager.triggerLinkReceived(room, trigger, event);
    }

    private static final class Window {
        private long start;
        private int count;
    }
}
