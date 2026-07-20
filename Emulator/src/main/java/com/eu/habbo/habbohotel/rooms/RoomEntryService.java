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
import java.util.function.IntFunction;

final class RoomEntryService {

    private final IntFunction<Room> roomLoader;
    private final IntFunction<Room> activeRooms;
    private final BiPredicate<Habbo, Room> entryAllowed;
    private final RoomOpener roomOpener;

    RoomEntryService(
            IntFunction<Room> roomLoader,
            IntFunction<Room> activeRooms,
            BiPredicate<Habbo, Room> entryAllowed,
            RoomOpener roomOpener) {
        this.roomLoader = roomLoader;
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
        Room room = this.roomLoader.apply(roomId);
        if (room == null) {
            return;
        }

        if (habbo.getHabboInfo().getLoadingRoom() != 0
                && room.getId() != habbo.getHabboInfo().getLoadingRoom()) {
            this.returnToHotel(habbo);
            return;
        }

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
            this.roomOpener.open(habbo, room, doorLocation, reconnectSpawn);
        } else if (room.getState() == RoomState.LOCKED) {
            this.requestDoorbell(habbo, room, roomId);
        } else if (room.getState() == RoomState.PASSWORD) {
            this.checkPassword(habbo, room, password, doorLocation, reconnectSpawn);
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

    private void checkPassword(Habbo habbo, Room room, String password, RoomTile doorLocation, boolean reconnectSpawn) {
        if (room.getPassword().equalsIgnoreCase(password)) {
            this.roomOpener.open(habbo, room, doorLocation, reconnectSpawn);
            return;
        }
        habbo.getClient().sendResponse(new GenericErrorMessagesComposer(GenericErrorCode.WRONG_ROOM_PASSWORD));
        this.returnToHotel(habbo);
    }

    private void returnToHotel(Habbo habbo) {
        habbo.getClient().sendResponse(new HotelViewComposer());
        habbo.getHabboInfo().setLoadingRoom(0);
    }

    @FunctionalInterface
    interface RoomOpener {
        void open(Habbo habbo, Room room, RoomTile doorLocation, boolean reconnectSpawn);
    }
}
