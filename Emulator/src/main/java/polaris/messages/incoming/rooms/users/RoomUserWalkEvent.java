package polaris.messages.incoming.rooms.users;

import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.items.interactions.wired.effects.WiredEffectMoveRotateUser;
import polaris.hotel.pets.PetTasks;
import polaris.hotel.rooms.BedProfile;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.rooms.RoomUnitStatus;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.core.WiredFreezeUtil;
import polaris.hotel.wired.core.WiredUserMovementHelper;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.users.RoomUnitOnRollerComposer;
import polaris.messages.outgoing.rooms.users.RoomUserStatusComposer;
import polaris.plugin.events.users.UserIdleEvent;

public class RoomUserWalkEvent extends MessageHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoomUserWalkEvent.class);
    public static final String CONTROL_KEY = "control";

    private static final String WALK_FLOOD_COUNT_KEY = "__walkFloodCount";
    private static final String WALK_FLOOD_WINDOW_KEY = "__walkFloodWindow";
    private static final String WALK_LAST_X_KEY = "__walkLastX";
    private static final String WALK_LAST_Y_KEY = "__walkLastY";

    private static final int MAX_WALKS_PER_SECOND = 15;

    @Override
    public int getRatelimit() {
        return 0;
    }

    @Override
    public void handle() throws Exception {
        if (this.client.getUser().getUserInfo().getCurrentRoom() == null) {
            return;
        }

        int x = this.packet.readInt();
        int y = this.packet.readInt();

        RoomUnit unit = this.client.getUser().getRoomUnit();
        if (unit != null) {
            long now = System.currentTimeMillis();
            Object windowObj = unit.getCacheable().get(WALK_FLOOD_WINDOW_KEY);
            Object countObj = unit.getCacheable().get(WALK_FLOOD_COUNT_KEY);

            long windowStart = (windowObj instanceof Long) ? (Long) windowObj : 0L;
            int count = (countObj instanceof Integer) ? (Integer) countObj : 0;

            if (now - windowStart > 1000) {
                windowStart = now;
                count = 0;
            }

            count++;
            unit.getCacheable().put(WALK_FLOOD_WINDOW_KEY, windowStart);
            unit.getCacheable().put(WALK_FLOOD_COUNT_KEY, count);

            if (count > MAX_WALKS_PER_SECOND) {
                unit.getCacheable().put(WALK_LAST_X_KEY, x);
                unit.getCacheable().put(WALK_LAST_Y_KEY, y);
                return;
            }

            Object lastX = unit.getCacheable().get(WALK_LAST_X_KEY);
            Object lastY = unit.getCacheable().get(WALK_LAST_Y_KEY);
            if (lastX != null && lastY != null) {
                x = (Integer) lastX;
                y = (Integer) lastY;
                unit.getCacheable().remove(WALK_LAST_X_KEY);
                unit.getCacheable().remove(WALK_LAST_Y_KEY);
            }
        }

        User user = getControlledUser();
        if (user == null) {
            return;
        }

        RoomUnit roomUnit = user.getRoomUnit();
        UserInfo userInfo = user.getUserInfo();
        Room room = userInfo.getCurrentRoom();

        try {
            if (roomUnit != null && roomUnit.isInRoom() && roomUnit.canWalk() && !WiredFreezeUtil.isFrozen(roomUnit)) {
                if (WiredUserMovementHelper.consumeSuppressedWalkCommand(roomUnit)) {
                    return;
                }

                if (roomUnit.cmdTeleport) {
                    handleTeleport(room, (short) x, (short) y, roomUnit, userInfo);
                    return;
                }

                if (userInfo.getRiding() != null
                        && ((userInfo.getRiding().getRoomUnit() != null
                                        && userInfo.getRiding().getRoomUnit().hasStatus(RoomUnitStatus.JUMP))
                                || (userInfo.getRiding().getTask() != null
                                        && userInfo.getRiding().getTask().equals(PetTasks.JUMP)))) {
                    return;
                }

                if (x == roomUnit.getX() && y == roomUnit.getY()) {
                    return;
                }

                if (room == null || room.getLayout() == null) {
                    return;
                }

                if (roomUnit.isIdle()) {
                    fireIdleEvent(user, roomUnit);
                }

                RoomTile tile = room.getLayout().getTile((short) x, (short) y);

                if (tile == null) {
                    return;
                }

                if (user.getRoomUnit().hasStatus(RoomUnitStatus.LAY)
                        && room.getLayout()
                                .getTilesInFront(
                                        user.getRoomUnit().getCurrentLocation(),
                                        user.getRoomUnit().getBodyRotation().getValue(),
                                        2)
                                .contains(tile)) {
                    return;
                }

                if (room.canLayAt(tile.x, tile.y) && handleLay(room, tile, (short) x, (short) y, roomUnit)) {
                    return;
                }

                Set<UserItem> items = room.getItemsAt(tile);

                if (!items.isEmpty()) {
                    for (UserItem item : items) {
                        RoomTile overriddenTile = item.getOverrideGoalTile(roomUnit, room, tile);

                        if (overriddenTile == null) {
                            return; // null cancels the entire event
                        }

                        if (!overriddenTile.equals(tile) && overriddenTile.isWalkable()) {
                            tile = overriddenTile;
                            break;
                        }
                    }
                }

                if (tile.isWalkable() || room.canSitOrLayAt(tile.x, tile.y) || roomUnit.canOverrideTile(tile)) {
                    if (WiredEffectMoveRotateUser.handleWalkWhileActive(room, roomUnit, tile)) {
                        return;
                    }

                    if (roomUnit.getMoveBlockingTask() != null) {
                        try {
                            roomUnit.getMoveBlockingTask().get(2, java.util.concurrent.TimeUnit.SECONDS);
                        } catch (java.util.concurrent.TimeoutException | java.util.concurrent.ExecutionException e) {
                            LOGGER.debug("Move blocking task did not finish cleanly", e);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }

                    boolean needsLocationResync = roomUnit.getCurrentLocation() != null
                            && (roomUnit.getPreviousLocation() == null
                                    || roomUnit.getPreviousLocation().x != roomUnit.getCurrentLocation().x
                                    || roomUnit.getPreviousLocation().y != roomUnit.getCurrentLocation().y
                                    || Math.abs(roomUnit.getPreviousLocationZ() - roomUnit.getZ()) > 0.01D);

                    if (WiredUserMovementHelper.shouldSuppressStatusComposer(roomUnit) || needsLocationResync) {
                        WiredUserMovementHelper.clearStatusComposerSuppression(roomUnit);
                        if (roomUnit.getCurrentLocation() != null) {
                            roomUnit.setPreviousLocation(roomUnit.getCurrentLocation());
                            roomUnit.setPreviousLocationZ(roomUnit.getZ());
                        }
                        room.sendComposer(new RoomUserStatusComposer(roomUnit).compose());
                    }

                    roomUnit.setGoalLocation(tile);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }
    }

    private static boolean handleLay(Room room, RoomTile tile, short x, short y, RoomUnit roomUnit) {
        UserItem bed = room.getTopItemAt(tile.x, tile.y);

        if (bed != null && bed.getBaseItem().allowLay()) {
            BedProfile profile = new BedProfile(bed);
            RoomTile pillow = profile.getPillow(room, x, y, bed);

            if (pillow == null) {
                return false;
            }

            // The pillow in the clicked lane; the nearest free lane only when that one is taken.
            pillow = profile.findFreePillow(room, bed, pillow, roomUnit);

            // Every pillow taken: nobody else's pillow is walked onto.
            if (pillow != null) {
                roomUnit.setGoalLocation(pillow);
            }
            return true;
        }
        return false;
    }

    private static void fireIdleEvent(User user, RoomUnit roomUnit) {
        UserIdleEvent event = new UserIdleEvent(user, UserIdleEvent.IdleReason.WALKED, false);
        Emulator.getPluginManager().fireEvent(event);

        if (!event.isCancelled() && !event.idle) {
            if (roomUnit.getRoom() != null) {
                roomUnit.getRoom().unIdle(user);
            }
            roomUnit.resetIdleTimer();
        }
    }

    private static void handleTeleport(Room room, short x, short y, RoomUnit roomUnit, UserInfo userInfo) {
        RoomTile t = room.getLayout().getTile(x, y);
        room.sendComposer(new RoomUnitOnRollerComposer(roomUnit, t, room).compose());

        if (userInfo.getRiding() != null) {
            room.sendComposer(new RoomUnitOnRollerComposer(userInfo.getRiding().getRoomUnit(), t, room).compose());
        }
    }

    private User getControlledUser() {
        User user = this.client.getUser();

        RoomUnit roomUnit = this.client.getUser().getRoomUnit();

        if (roomUnit.isTeleporting) {
            return null;
        }

        if (roomUnit.isKicked) {
            return null;
        }

        if (roomUnit.getCacheable().get(CONTROL_KEY) != null) {
            user = (User) roomUnit.getCacheable().get(CONTROL_KEY);

            if (user.getUserInfo().getCurrentRoom()
                    != this.client.getUser().getUserInfo().getCurrentRoom()) {
                user.getRoomUnit().getCacheable().remove("controller");
                this.client.getUser().getRoomUnit().getCacheable().remove(CONTROL_KEY);
                user = this.client.getUser();
            }
        }
        return user;
    }
}
