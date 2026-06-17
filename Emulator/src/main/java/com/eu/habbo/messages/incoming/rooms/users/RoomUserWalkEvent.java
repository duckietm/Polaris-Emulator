package com.eu.habbo.messages.incoming.rooms.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.pets.PetTasks;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.rooms.BedProfile;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectMoveRotateUser;
import com.eu.habbo.habbohotel.wired.core.WiredFreezeUtil;
import com.eu.habbo.habbohotel.wired.core.WiredUserMovementHelper;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUnitOnRollerComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;
import com.eu.habbo.plugin.events.users.UserIdleEvent;
import gnu.trove.set.hash.THashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    if (this.client.getHabbo().getHabboInfo().getCurrentRoom() == null) {
      return;
    }

    int x = this.packet.readInt();
    int y = this.packet.readInt();

    RoomUnit unit = this.client.getHabbo().getRoomUnit();
    if (unit != null) {
      long now = System.currentTimeMillis();
      Object windowObj = unit.getCacheable().get(WALK_FLOOD_WINDOW_KEY);
      Object countObj = unit.getCacheable().get(WALK_FLOOD_COUNT_KEY);

      long windowStart = (windowObj instanceof Long) ? (Long) windowObj : 0L;
      int count = (countObj instanceof Integer) ? (Integer) countObj : 0;

      if (now - windowStart > 1000) {
        // New 1-second window
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

    Habbo habbo = getControlledHabbo();
    if (habbo == null) {
      return;
    }

    RoomUnit roomUnit = habbo.getRoomUnit();
    HabboInfo habboInfo = habbo.getHabboInfo();
    Room room = habboInfo.getCurrentRoom();

    try {
      if (roomUnit != null && roomUnit.isInRoom() && roomUnit.canWalk() && !WiredFreezeUtil.isFrozen(roomUnit)) {
        if (WiredUserMovementHelper.consumeSuppressedWalkCommand(roomUnit)) {
          return;
        }

        if (roomUnit.cmdTeleport) {
          handleTeleport(room, (short) x, (short) y, roomUnit, habboInfo);
          return;
        }

        // Don't calculate a new path if we are on a horse
        if (habboInfo.getRiding() != null && habboInfo.getRiding().getTask() != null
            && habboInfo.getRiding().getTask().equals(PetTasks.JUMP)) {
          return;
        }

        // Don't calulcate a new path if are already at the end position
        if (x == roomUnit.getX() && y == roomUnit.getY()) {
          return;
        }

        if (room == null || room.getLayout() == null) {
          return;
        }

        if (roomUnit.isIdle()) {
          fireIdleEvent(habbo, roomUnit);
        }

        RoomTile tile = room.getLayout().getTile((short) x, (short) y);

        // this should never happen, if it does it would be a design flaw
        if (tile == null) {
          return;
        }

        if (habbo.getRoomUnit().hasStatus(RoomUnitStatus.LAY) && room.getLayout()
            .getTilesInFront(habbo.getRoomUnit().getCurrentLocation(),
                habbo.getRoomUnit().getBodyRotation().getValue(), 2).contains(tile)) {
          return;
        }

        if (room.canLayAt(tile.x, tile.y) && handleLay(room, tile, (short) x, (short) y,
            roomUnit)) {
          return;
        }

        THashSet<HabboItem> items = room.getItemsAt(tile);

        if (!items.isEmpty()) {
          for (HabboItem item : items) {
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

        // This is where we set the end location and begin finding a path
        if (tile.isWalkable() || room.canSitOrLayAt(tile.x, tile.y)) {
          if (WiredEffectMoveRotateUser.handleWalkWhileActive(room, roomUnit, tile)) {
            return;
          }

          if (roomUnit.getMoveBlockingTask() != null) {
            try {
              // Bound the wait so a stuck/delayed move-blocking task can't park
              // the Netty event loop (and thus every client on it) indefinitely.
              roomUnit.getMoveBlockingTask().get(2, java.util.concurrent.TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException | java.util.concurrent.ExecutionException | InterruptedException e) {
              // proceed with the walk regardless
            }
          }

          boolean needsLocationResync =
              roomUnit.getCurrentLocation() != null
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
    HabboItem bed = room.getTopItemAt(tile.x, tile.y);

    if (bed != null && bed.getBaseItem().allowLay()) {
      BedProfile profile = new BedProfile(bed);
      RoomTile pillow = profile.getPillow(room, x, y, bed);

      // If pillow position is occupied, try the other side (double beds only)
      if (pillow != null && !room.canLayAt(pillow.x, pillow.y)) {
        pillow = profile.getOtherSide(room, bed, pillow);
      }

      if (pillow != null && room.canLayAt(pillow.x, pillow.y)) {
        roomUnit.setGoalLocation(pillow);
        return true;
      }
    }
    return false;
  }

  private static void fireIdleEvent(Habbo habbo, RoomUnit roomUnit) {
    UserIdleEvent event = new UserIdleEvent(habbo, UserIdleEvent.IdleReason.WALKED, false);
    Emulator.getPluginManager().fireEvent(event);

    if (!event.isCancelled() && !event.idle) {
      if (roomUnit.getRoom() != null) {
        roomUnit.getRoom().unIdle(habbo);
      }
      roomUnit.resetIdleTimer();
    }
  }

  private static void handleTeleport(Room room, short x, short y, RoomUnit roomUnit,
      HabboInfo habboInfo) {
    RoomTile t = room.getLayout().getTile(x, y);
    room.sendComposer(new RoomUnitOnRollerComposer(roomUnit, t, room).compose());

    if (habboInfo.getRiding() != null) {
      room.sendComposer(
          new RoomUnitOnRollerComposer(habboInfo.getRiding().getRoomUnit(), t, room).compose());
    }
  }

  private Habbo getControlledHabbo() {
    Habbo habbo = this.client.getHabbo();

    RoomUnit roomUnit = this.client.getHabbo().getRoomUnit();

    if (roomUnit.isTeleporting) {
      return null;
    }

    if (roomUnit.isKicked) {
      return null;
    }

    if (roomUnit.getCacheable().get(CONTROL_KEY) != null) {
      habbo = (Habbo) roomUnit.getCacheable().get(CONTROL_KEY);

      if (habbo.getHabboInfo().getCurrentRoom() != this.client.getHabbo().getHabboInfo()
          .getCurrentRoom()) {
        habbo.getRoomUnit().getCacheable().remove("controller");
        this.client.getHabbo().getRoomUnit().getCacheable().remove(CONTROL_KEY);
        habbo = this.client.getHabbo();
      }
    }
    return habbo;
  }
}
