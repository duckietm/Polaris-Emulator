package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserHandItemComposer;
import com.eu.habbo.threading.ThreadPooling;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * One bot bringing a user a hand item, as AIR's Bot Give Hand Item does. The bot takes the item at once. Next to the
 * user it hands it over straight away; otherwise it walks to the free tile next to the user that takes the fewest
 * steps (then the nearest, then a random one), following the user when they move, and hands it over 300 ms after it
 * got next to them. A user it cannot reach is not served: the bot keeps the item. A user who leaves meanwhile is not
 * served either: the bot drops the item once it reaches the spot.
 */
final class BotHandItemDelivery implements Runnable {
    static final long POLL_MS = 100L;
    static final long HAND_OVER_MS = 300L;
    static final long GIVE_UP_MS = 60_000L;

    private final Room room;
    private final RoomUnit bot;
    private final RoomUnit user;
    private final int itemId;
    private final Random random;
    private final long startedAt;
    private RoomTile lastUserTile;
    private long nextToSince = -1L;

    BotHandItemDelivery(Room room, RoomUnit bot, RoomUnit user, int itemId, Random random, long now) {
        this.room = room;
        this.bot = bot;
        this.user = user;
        this.itemId = itemId;
        this.random = random;
        this.startedAt = now;
        this.lastUserTile = user.getCurrentLocation();
    }

    static void start(Room room, RoomUnit bot, RoomUnit user, int itemId) {
        if (room == null || bot == null || user == null || user.getCurrentLocation() == null) {
            return;
        }

        BotHandItemDelivery delivery = new BotHandItemDelivery(
                room, bot, user, itemId, ThreadLocalRandom.current(), System.currentTimeMillis());
        if (delivery.begin()) {
            delivery.pollAgain();
        }
    }

    /** Takes the item and hands it over or sets off; false when nothing is left to do. */
    boolean begin() {
        giveHandItem(this.room, this.bot, this.itemId);

        if (nextTo(this.bot.getCurrentLocation(), this.lastUserTile)) {
            this.handOver();
            return false;
        }

        return this.route();
    }

    @Override
    public void run() {
        if (this.tick(System.currentTimeMillis())) {
            this.pollAgain();
        }
    }

    /** One poll; true while the delivery goes on. */
    boolean tick(long now) {
        if (!this.bot.isInRoom() || this.bot.getRoom() != this.room || now - this.startedAt > GIVE_UP_MS) {
            return false;
        }

        boolean present = this.user.isInRoom() && this.user.getRoom() == this.room;
        RoomTile userTile = present ? this.user.getCurrentLocation() : this.lastUserTile;

        if (userTile != null && nextTo(this.bot.getCurrentLocation(), userTile)) {
            if (this.nextToSince < 0) {
                this.nextToSince = now;
            }
            if (now - this.nextToSince >= HAND_OVER_MS) {
                if (present) {
                    this.handOver();
                } else {
                    giveHandItem(this.room, this.bot, 0);
                }
                return false;
            }
            return true;
        }

        this.nextToSince = -1L;

        // The user moved: AIR re-routes the bot to them.
        if (present && userTile != null && !userTile.equals(this.lastUserTile)) {
            this.lastUserTile = userTile;
            return this.route();
        }

        return true;
    }

    private boolean route() {
        RoomTile tile = bestTileNextTo(this.room, this.lastUserTile, this.bot, this.random);
        if (tile == null) {
            // Boxed in: the bot keeps the item and stays.
            return false;
        }

        this.bot.setGoalLocation(tile);
        this.bot.setCanWalk(true);
        boolean reachable = tile.equals(this.bot.getCurrentLocation())
                || (this.bot.getPath() != null && !this.bot.getPath().isEmpty());
        return reachable;
    }

    private void handOver() {
        giveHandItem(this.room, this.user, this.itemId);
        giveHandItem(this.room, this.bot, 0);
    }

    private void pollAgain() {
        ThreadPooling threading = WiredPlatform.threading();
        if (threading != null) {
            threading.run(this, POLL_MS);
        }
    }

    static void giveHandItem(Room room, RoomUnit unit, int itemId) {
        if (room == null || unit == null || !unit.isInRoom()) {
            return;
        }

        unit.setHandItem(itemId);
        room.sendComposer(new RoomUserHandItemComposer(unit).compose());
    }

    static boolean nextTo(RoomTile a, RoomTile b) {
        return a != null && b != null && Math.max(Math.abs(a.x - b.x), Math.abs(a.y - b.y)) <= 1;
    }

    /**
     * The free tile next to the user the bot takes: the fewest steps away (a diagonal is one step), then the nearest,
     * then any of the rest at random.
     */
    static RoomTile bestTileNextTo(Room room, RoomTile userTile, RoomUnit bot, Random random) {
        if (room == null || room.getLayout() == null || userTile == null || bot.getCurrentLocation() == null) {
            return null;
        }

        RoomTile from = bot.getCurrentLocation();
        List<RoomTile> free = new ArrayList<>(8);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                RoomTile tile = room.getLayout().getTile((short) (userTile.x + dx), (short) (userTile.y + dy));
                if (tile == null || tile.state == RoomTileState.INVALID || tile.state == RoomTileState.BLOCKED) {
                    continue;
                }
                if (room.getRoomUnitsAt(tile).stream().anyMatch(unit -> unit != bot)) {
                    continue;
                }
                free.add(tile);
            }
        }

        if (free.isEmpty()) {
            return null;
        }

        Comparator<RoomTile> order = Comparator.<RoomTile>comparingInt(
                        tile -> Math.max(Math.abs(tile.x - from.x), Math.abs(tile.y - from.y)))
                .thenComparingInt(
                        tile -> (tile.x - from.x) * (tile.x - from.x) + (tile.y - from.y) * (tile.y - from.y));
        free.sort(order);
        RoomTile best = free.get(0);
        List<RoomTile> ties =
                free.stream().filter(tile -> order.compare(tile, best) == 0).toList();
        return ties.get(random.nextInt(ties.size()));
    }
}
