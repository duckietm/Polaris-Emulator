package polaris.hotel.rooms;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import polaris.hotel.permissions.Permission;
import polaris.hotel.users.User;
import polaris.messages.outgoing.generic.alerts.GenericErrorCode;
import polaris.messages.outgoing.generic.alerts.GenericErrorMessagesComposer;
import polaris.messages.outgoing.hotelview.HotelViewComposer;
import polaris.messages.outgoing.rooms.DoorbellAddUserComposer;
import polaris.messages.outgoing.rooms.RoomAccessDeniedComposer;
import polaris.messages.outgoing.rooms.RoomEnterErrorComposer;

/**
 * Room entry: resolves the room without its contents, checks access with the rights and bans
 * only, and loads the full room (items, bots, pets, heightmap, wired) once entry is granted.
 */
final class RoomEntryService {
    /** Wrong passwords one user may try at one room before the room stops checking them, for ten minutes. */
    static final int MAX_PASSWORD_FAILURES = 5;

    // Counted per user and room: a client could try passwords in a loop, two a second, for as long as it liked.
    private static final Map<String, AtomicInteger> PASSWORD_FAILURES = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterWrite(Duration.ofMinutes(10))
            .<String, AtomicInteger>build()
            .asMap();

    private final IntFunction<Room> roomLookup;
    private final Consumer<Room> accessDataLoader;
    private final Consumer<Room> contentLoader;
    private final IntFunction<Room> activeRooms;
    private final BiPredicate<User, Room> entryAllowed;
    private final RoomOpener roomOpener;
    private final BiPredicate<User, Room> ownerBlocks;

    RoomEntryService(
            IntFunction<Room> roomLookup,
            Consumer<Room> accessDataLoader,
            Consumer<Room> contentLoader,
            IntFunction<Room> activeRooms,
            BiPredicate<User, Room> entryAllowed,
            RoomOpener roomOpener) {
        this(roomLookup, accessDataLoader, contentLoader, activeRooms, entryAllowed, roomOpener, (user, room) -> false);
    }

    /** @param ownerBlocks whether the room's owner blocked the user, who is then refused at the door */
    RoomEntryService(
            IntFunction<Room> roomLookup,
            Consumer<Room> accessDataLoader,
            Consumer<Room> contentLoader,
            IntFunction<Room> activeRooms,
            BiPredicate<User, Room> entryAllowed,
            RoomOpener roomOpener,
            BiPredicate<User, Room> ownerBlocks) {
        this.roomLookup = roomLookup;
        this.accessDataLoader = accessDataLoader;
        this.contentLoader = contentLoader;
        this.activeRooms = activeRooms;
        this.entryAllowed = entryAllowed;
        this.roomOpener = roomOpener;
        this.ownerBlocks = ownerBlocks;
    }

    void enter(
            User user,
            int roomId,
            String password,
            boolean overrideChecks,
            RoomTile doorLocation,
            boolean reconnectSpawn) {
        this.enter(user, roomId, password, overrideChecks, Spawn.at(doorLocation, reconnectSpawn));
    }

    void enter(User user, int roomId, String password, boolean overrideChecks, Spawn spawn) {
        Room room = this.roomLookup.apply(roomId);
        if (room == null) {
            return;
        }

        if (user.getUserInfo().getLoadingRoom() != 0
                && room.getId() != user.getUserInfo().getLoadingRoom()) {
            this.returnToHotel(user);
            return;
        }

        this.accessDataLoader.accept(room);

        if (!this.entryAllowed.test(user, room) && user.getUserInfo().getCurrentRoom() == null) {
            this.returnToHotel(user);
            return;
        }

        if (room.isBanned(user)
                && !user.hasPermission(Permission.ACC_ANYROOMOWNER)
                && !user.hasPermission(Permission.ACC_ENTERANYROOM)) {
            user.getClient().sendResponse(new RoomEnterErrorComposer(RoomEnterErrorComposer.ROOM_ERROR_BANNED));
            return;
        }

        // The official "blocked by the room owner": a player the owner blocked does not get in.
        if (this.ownerBlocks.test(user, room)) {
            user.getClient().sendResponse(new RoomEnterErrorComposer(RoomEnterErrorComposer.ROOM_ERROR_BLOCKED));
            return;
        }

        if (room.isBuildersClubTrialLocked()
                && user.getUserInfo().getId() != room.getOwnerId()
                && !overrideChecks
                && !user.hasPermission(Permission.ACC_ANYROOMOWNER)
                && !user.hasPermission(Permission.ACC_ENTERANYROOM)) {
            BuildersClubRoomSupport.sendVisitDeniedOwnerBubble(
                    room.getOwnerId(), user.getUserInfo().getUsername());
            BuildersClubRoomSupport.sendVisitDeniedVisitorAlert(
                    user.getUserInfo().getId());
            this.returnToHotel(user);
            return;
        }

        if (user.getUserInfo().getRoomQueueId() != roomId) {
            Room queuedRoom = this.activeRooms.apply(roomId);
            if (queuedRoom != null) {
                queuedRoom.removeFromQueue(user);
            }
        }

        if (this.canOpen(user, room, overrideChecks)) {
            this.open(user, room, spawn);
        } else if (room.getState() == RoomState.LOCKED) {
            this.requestDoorbell(user, room, roomId);
        } else if (room.getState() == RoomState.PASSWORD) {
            this.checkPassword(user, room, password, spawn);
        } else {
            this.returnToHotel(user);
        }
    }

    private boolean canOpen(User user, Room room, boolean overrideChecks) {
        return overrideChecks
                || room.isOwner(user)
                || room.getState() == RoomState.OPEN
                || user.hasPermission(Permission.ACC_ANYROOMOWNER)
                || user.hasPermission(Permission.ACC_ENTERANYROOM)
                || room.hasRights(user)
                || (room.getState().equals(RoomState.INVISIBLE) && room.hasRights(user))
                || (room.hasGuild() && room.getGuildRightLevel(user).isGreaterThan(RoomRightLevels.GUILD_RIGHTS));
    }

    private void requestDoorbell(User user, Room room, int roomId) {
        boolean rightsFound = false;
        synchronized (room.roomUnitLock) {
            for (User current : room.getUsers()) {
                if (room.hasRights(current)
                        || current.getUserInfo().getId() == room.getOwnerId()
                        || (room.hasGuild()
                                && room.getGuildRightLevel(current)
                                        .isEqualOrGreaterThan(RoomRightLevels.GUILD_RIGHTS))) {
                    current.getClient()
                            .sendResponse(new DoorbellAddUserComposer(
                                    user.getUserInfo().getUsername()));
                    rightsFound = true;
                }
            }
        }

        if (!rightsFound) {
            user.getClient().sendResponse(new RoomAccessDeniedComposer(room.getId(), ""));
            this.returnToHotel(user);
            return;
        }

        user.getUserInfo().setRoomQueueId(roomId);
        user.getClient().sendResponse(new DoorbellAddUserComposer(""));
        room.addToQueue(user);
    }

    private void checkPassword(User user, Room room, String password, Spawn spawn) {
        String key = user.getUserInfo().getId() + ":" + room.getId();

        if (passwordAttemptsLeft(PASSWORD_FAILURES.get(key))
                && room.getPassword().equalsIgnoreCase(password)) {
            PASSWORD_FAILURES.remove(key);
            this.open(user, room, spawn);
            return;
        }
        PASSWORD_FAILURES.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
        user.getClient().sendResponse(new GenericErrorMessagesComposer(GenericErrorCode.WRONG_ROOM_PASSWORD));
        this.returnToHotel(user);
    }

    static boolean passwordAttemptsLeft(AtomicInteger failures) {
        return failures == null || failures.get() < MAX_PASSWORD_FAILURES;
    }

    private void open(User user, Room room, Spawn spawn) {
        this.contentLoader.accept(room);
        // Reconnect coordinates need the layout, which a cold room only has after the content load.
        RoomTile doorLocation = spawn.resolve(room);
        this.roomOpener.open(user, room, doorLocation, spawn.reconnect() && doorLocation != null);
    }

    private void returnToHotel(User user) {
        user.getClient().sendResponse(new HotelViewComposer());
        user.getUserInfo().setLoadingRoom(0);
    }

    /**
     * Where the user appears: a fixed tile (door when null, or a teleport), or reconnect
     * coordinates that are only placed on a walkable tile of the loaded layout.
     */
    record Spawn(RoomTile tile, int x, int y, boolean reconnect) {

        static Spawn at(RoomTile tile, boolean reconnect) {
            return new Spawn(tile, -1, -1, reconnect);
        }

        static Spawn reconnectAt(int x, int y) {
            return new Spawn(null, x, y, true);
        }

        RoomTile resolve(Room room) {
            if (this.tile != null || this.x < 0 || this.y < 0) {
                return this.tile;
            }

            RoomLayout layout = room.getLayout();
            if (layout == null) {
                return null;
            }

            RoomTile spawnTile = layout.getTile((short) this.x, (short) this.y);
            return spawnTile != null && spawnTile.isWalkable() ? spawnTile : null;
        }
    }

    @FunctionalInterface
    interface RoomOpener {
        void open(User user, Room room, RoomTile doorLocation, boolean reconnectSpawn);
    }
}
