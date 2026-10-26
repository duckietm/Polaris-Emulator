package com.eu.habbo.threading.runnables;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A bot following a user, as AIR's Bot Follow does: every room tick it waits while walking; once it stopped and is
 * not next to the user it walks to a random free tile around them; next to them it stays put, and Bot Reached Avatar
 * fires once for an arrival (not for a user who walks up to it). The follow belongs to the account: while the user
 * is out of the room the bot waits, and it follows again when they come back.
 */
public class BotFollowHabbo implements Runnable {
    static final long TICK_MS = 500L;

    private final Bot bot;
    private final int habboId;
    private final Room room;
    private final int generation;
    private boolean hasReached;
    private RoomTile goal;

    public BotFollowHabbo(Bot bot, Habbo habbo, Room room) {
        this.bot = bot;
        this.habboId = (habbo != null && habbo.getHabboInfo() != null)
                ? habbo.getHabboInfo().getId()
                : 0;
        this.room = room;
        this.generation = bot != null ? bot.getFollowGeneration() : 0;
        // A bot told to follow a user it already stands next to has not reached them: Habbo raises Bot
        // Reached Avatar only on an arrival.
        this.hasReached = startsNextTo(bot, habbo);
    }

    static boolean startsNextTo(Bot bot, Habbo habbo) {
        if (bot == null || habbo == null || bot.getRoomUnit() == null || habbo.getRoomUnit() == null) {
            return false;
        }

        RoomTile botTile = bot.getRoomUnit().getCurrentLocation();
        RoomTile habboTile = habbo.getRoomUnit().getCurrentLocation();
        return botTile != null && habboTile != null && habboTile.distance(botTile) < 2;
    }

    boolean hasReached() {
        return this.hasReached;
    }

    @Override
    public void run() {
        if (!this.stillFollowing()) {
            return;
        }

        this.step(ThreadLocalRandom.current());
        Emulator.getThreading().run(this, TICK_MS);
    }

    private boolean stillFollowing() {
        return this.bot != null
                && this.room != null
                && this.bot.getRoom() == this.room
                && this.habboId > 0
                && this.bot.getFollowingHabboId() == this.habboId
                && this.bot.getFollowGeneration() == this.generation;
    }

    /** One room tick of the follow. */
    void step(Random random) {
        RoomUnit botUnit = this.bot.getRoomUnit();
        Habbo habbo = this.room.getHabbo(this.habboId);

        // The user left: the bot waits for them to come back.
        if (botUnit == null || habbo == null || habbo.getRoomUnit() == null || botUnit.isWalking()) {
            return;
        }

        RoomTile userTile = habbo.getRoomUnit().getCurrentLocation();
        RoomTile botTile = botUnit.getCurrentLocation();
        if (userTile == null || botTile == null) {
            return;
        }

        boolean arrived = this.goal != null && this.goal.equals(botTile);
        this.goal = null;

        if (nextTo(botTile, userTile)) {
            if (!this.hasReached && arrived) {
                WiredManager.triggerBotReachedHabbo(this.room, botUnit, habbo.getRoomUnit());
            }
            this.hasReached = true;
            return;
        }

        this.hasReached = false;
        RoomTile target = randomFreeTileAround(this.room, userTile, botUnit, random);
        if (target != null) {
            this.goal = target;
            botUnit.setGoalLocation(target);
            botUnit.setCanWalk(true);
        }
    }

    static boolean nextTo(RoomTile a, RoomTile b) {
        return Math.max(Math.abs(a.x - b.x), Math.abs(a.y - b.y)) <= 1;
    }

    /** A random one of the 8 tiles around the user that is open and has nobody else on it. */
    static RoomTile randomFreeTileAround(Room room, RoomTile center, RoomUnit self, Random random) {
        if (room.getLayout() == null) {
            return null;
        }

        List<RoomTile> free = new ArrayList<>(8);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                RoomTile tile = room.getLayout().getTile((short) (center.x + dx), (short) (center.y + dy));
                if (tile == null || tile.state == RoomTileState.INVALID || tile.state == RoomTileState.BLOCKED) {
                    continue;
                }
                if (room.getRoomUnitsAt(tile).stream().anyMatch(unit -> unit != self)) {
                    continue;
                }
                free.add(tile);
            }
        }

        return free.isEmpty() ? null : free.get(random.nextInt(free.size()));
    }
}
