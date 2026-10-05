package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.generic.alerts.GenericErrorCode;
import com.eu.habbo.messages.outgoing.generic.alerts.GenericErrorMessagesComposer;
import com.eu.habbo.messages.outgoing.hotelview.HotelViewComposer;
import com.eu.habbo.messages.outgoing.rooms.DoorbellAddUserComposer;
import com.eu.habbo.messages.outgoing.rooms.RoomAccessDeniedComposer;
import com.eu.habbo.messages.outgoing.rooms.RoomEnterErrorComposer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/**
 * Room entry: resolves the room without its contents, checks access with the rights and bans
 * only, and loads the full room (items, bots, pets, heightmap, wired) once entry is granted.
 */
final class RoomEntryService {

    private final IntFunction<Room> roomLookup;
    private final Consumer<Room> accessDataLoader;
    private final Consumer<Room> contentLoader;
    private final IntFunction<Room> activeRooms;
    private final BiPredicate<Habbo, Room> entryAllowed;
    private final RoomOpener roomOpener;

    RoomEntryService(
            IntFunction<Room> roomLookup,
            Consumer<Room> accessDataLoader,
            Consumer<Room> contentLoader,
            IntFunction<Room> activeRooms,
            BiPredicate<Habbo, Room> entryAllowed,
            RoomOpener roomOpener) {
        this.roomLookup = roomLookup;
        this.accessDataLoader = accessDataLoader;
        this.contentLoader = contentLoader;
        this.activeRooms = activeRooms;
        this.entryAllowed = entryAllowed;
        this.roomOpener = roomOpener;
    }

    void enter(
            Habbo habbo,
            int roomId,
            String password,
            boolean overrideChecks,
            RoomTile doorLocation,
            boolean reconnectSpawn) {
        this.enter(habbo, roomId, password, overrideChecks, Spawn.at(doorLocation, reconnectSpawn));
    }

    void enter(Habbo habbo, int roomId, String password, boolean overrideChecks, Spawn spawn) {
        Room room = this.roomLookup.apply(roomId);
        if (room == null) {
            return;
        }

        if (habbo.getHabboInfo().getLoadingRoom() != 0
                && room.getId() != habbo.getHabboInfo().getLoadingRoom()) {
            this.returnToHotel(habbo);
            return;
        }

        this.accessDataLoader.accept(room);

        if (!this.entryAllowed.test(habbo, room) && habbo.getHabboInfo().getCurrentRoom() == null) {
            this.returnToHotel(habbo);
            return;
        }

        if (room.isBanned(habbo)
                && !habbo.hasPermission(Permission.ACC_ANYROOMOWNER)
                && !habbo.hasPermission(Permission.ACC_ENTERANYROOM)) {
            habbo.getClient().sendResponse(new RoomEnterErrorComposer(RoomEnterErrorComposer.ROOM_ERROR_BANNED));
            return;
        }

        if (room.isBuildersClubTrialLocked()
                && habbo.getHabboInfo().getId() != room.getOwnerId()
                && !overrideChecks
                && !habbo.hasPermission(Permission.ACC_ANYROOMOWNER)
                && !habbo.hasPermission(Permission.ACC_ENTERANYROOM)) {
            BuildersClubRoomSupport.sendVisitDeniedOwnerBubble(
                    room.getOwnerId(), habbo.getHabboInfo().getUsername());
            BuildersClubRoomSupport.sendVisitDeniedVisitorAlert(
                    habbo.getHabboInfo().getId());
            this.returnToHotel(habbo);
            return;
        }

        if (habbo.getHabboInfo().getRoomQueueId() != roomId) {
            Room queuedRoom = this.activeRooms.apply(roomId);
            if (queuedRoom != null) {
                queuedRoom.removeFromQueue(habbo);
            }
        }

        if (this.canOpen(habbo, room, overrideChecks)) {
            this.open(habbo, room, spawn);
        } else if (room.getState() == RoomState.LOCKED) {
            this.requestDoorbell(habbo, room, roomId);
        } else if (room.getState() == RoomState.PASSWORD) {
            this.checkPassword(habbo, room, password, spawn);
        } else {
            this.returnToHotel(habbo);
        }
    }

    private boolean canOpen(Habbo habbo, Room room, boolean overrideChecks) {
        return overrideChecks
                || room.isOwner(habbo)
                || room.getState() == RoomState.OPEN
                || habbo.hasPermission(Permission.ACC_ANYROOMOWNER)
                || habbo.hasPermission(Permission.ACC_ENTERANYROOM)
                || room.hasRights(habbo)
                || (room.getState().equals(RoomState.INVISIBLE) && room.hasRights(habbo))
                || (room.hasGuild() && room.getGuildRightLevel(habbo).isGreaterThan(RoomRightLevels.GUILD_RIGHTS));
    }

    private void requestDoorbell(Habbo habbo, Room room, int roomId) {
        boolean rightsFound = false;
        synchronized (room.roomUnitLock) {
            for (Habbo current : room.getHabbos()) {
                if (room.hasRights(current)
                        || current.getHabboInfo().getId() == room.getOwnerId()
                        || (room.hasGuild()
                                && room.getGuildRightLevel(current)
                                        .isEqualOrGreaterThan(RoomRightLevels.GUILD_RIGHTS))) {
                    current.getClient()
                            .sendResponse(new DoorbellAddUserComposer(
                                    habbo.getHabboInfo().getUsername()));
                    rightsFound = true;
                }
            }
        }

        if (!rightsFound) {
            habbo.getClient().sendResponse(new RoomAccessDeniedComposer(""));
            this.returnToHotel(habbo);
            return;
        }

        habbo.getHabboInfo().setRoomQueueId(roomId);
        habbo.getClient().sendResponse(new DoorbellAddUserComposer(""));
        room.addToQueue(habbo);
    }

    private void checkPassword(Habbo habbo, Room room, String password, Spawn spawn) {
        if (room.getPassword().equalsIgnoreCase(password)) {
            this.open(habbo, room, spawn);
            return;
        }
        habbo.getClient().sendResponse(new GenericErrorMessagesComposer(GenericErrorCode.WRONG_ROOM_PASSWORD));
        this.returnToHotel(habbo);
    }

    private void open(Habbo habbo, Room room, Spawn spawn) {
        this.contentLoader.accept(room);
        // Reconnect coordinates need the layout, which a cold room only has after the content load.
        RoomTile doorLocation = spawn.resolve(room);
        this.roomOpener.open(habbo, room, doorLocation, spawn.reconnect() && doorLocation != null);
    }

    private void returnToHotel(Habbo habbo) {
        habbo.getClient().sendResponse(new HotelViewComposer());
        habbo.getHabboInfo().setLoadingRoom(0);
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
        void open(Habbo habbo, Room room, RoomTile doorLocation, boolean reconnectSpawn);
    }
}
