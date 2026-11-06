package polaris.threading.runnables;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import polaris.Emulator;
import polaris.hotel.bots.Bot;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomTileState;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.wired.core.WiredManager;

/**
 * A bot following a user, as AIR's Bot Follow does: every room tick it waits while walking; once it stopped and is
 * not next to the user it walks to a random free tile around them; next to them it stays put, and Bot Reached Avatar
 * fires once for an arrival (not for a user who walks up to it). The follow belongs to the account: while the user
 * is out of the room the bot waits, and it follows again when they come back.
 */
public class BotFollowUser implements Runnable {
    static final long TICK_MS = 500L;

    private final Bot bot;
    private final int userId;
    private final Room room;
    private final int generation;
    private boolean hasReached;
    private RoomTile goal;

    public BotFollowUser(Bot bot, User user, Room room) {
        this.bot = bot;
        this.userId = (user != null && user.getUserInfo() != null)
                ? user.getUserInfo().getId()
                : 0;
        this.room = room;
        this.generation = bot != null ? bot.getFollowGeneration() : 0;
        // A bot told to follow a user it already stands next to has not reached them: Habbo raises Bot
        // Reached Avatar only on an arrival.
        this.hasReached = startsNextTo(bot, user);
    }

    static boolean startsNextTo(Bot bot, User user) {
        if (bot == null || user == null || bot.getRoomUnit() == null || user.getRoomUnit() == null) {
            return false;
        }

        RoomTile botTile = bot.getRoomUnit().getCurrentLocation();
        RoomTile userTile = user.getRoomUnit().getCurrentLocation();
        return botTile != null && userTile != null && userTile.distance(botTile) < 2;
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
                && this.userId > 0
                && this.bot.getFollowingUserId() == this.userId
                && this.bot.getFollowGeneration() == this.generation;
    }

    /** One room tick of the follow. */
    void step(Random random) {
        RoomUnit botUnit = this.bot.getRoomUnit();
        User user = this.room.getUser(this.userId);

        // The user left: the bot waits for them to come back.
        if (botUnit == null || user == null || user.getRoomUnit() == null || botUnit.isWalking()) {
            return;
        }

        RoomTile userTile = user.getRoomUnit().getCurrentLocation();
        RoomTile botTile = botUnit.getCurrentLocation();
        if (userTile == null || botTile == null) {
            return;
        }

        boolean arrived = this.goal != null && this.goal.equals(botTile);
        this.goal = null;

        if (nextTo(botTile, userTile)) {
            if (!this.hasReached && arrived) {
                WiredManager.triggerBotReachedUser(this.room, botUnit, user.getRoomUnit());
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
