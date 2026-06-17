package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionTileWalkMagic;
import com.eu.habbo.habbohotel.items.interactions.InteractionStackWalkHelper;
import com.eu.habbo.habbohotel.items.interactions.InteractionWater;
import com.eu.habbo.habbohotel.items.interactions.InteractionWaterItem;
import com.eu.habbo.habbohotel.items.interactions.interfaces.ConditionalGate;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.pets.RideablePet;
import com.eu.habbo.habbohotel.wired.core.WiredMoveCarryHelper;
import com.eu.habbo.habbohotel.wired.core.WiredUserMovementHelper;
import com.eu.habbo.habbohotel.users.DanceType;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;
import com.eu.habbo.plugin.Event;
import com.eu.habbo.plugin.events.roomunit.RoomUnitLookAtPointEvent;
import com.eu.habbo.plugin.events.roomunit.RoomUnitSetGoalEvent;
import com.eu.habbo.plugin.events.users.UserIdleEvent;
import com.eu.habbo.plugin.events.users.UserTakeStepEvent;
import com.eu.habbo.threading.runnables.RoomUnitKick;
import com.eu.habbo.util.pathfinding.Rotation;
import gnu.trove.map.TMap;
import gnu.trove.map.hash.THashMap;
import gnu.trove.set.hash.THashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;

public class RoomUnit {

  private static final Logger LOGGER = LoggerFactory.getLogger(RoomUnit.class);

  public boolean isWiredTeleporting = false;
  public boolean isLeavingTeleporter = false;
  private final ConcurrentHashMap<RoomUnitStatus, String> status;
  private final THashMap<String, Object> cacheable;
  public boolean canRotate = true;
  public boolean animateWalk = false;
  public boolean cmdTeleport = false;
  public boolean cmdSit = false;
  public boolean cmdStand = false;
  public boolean cmdLay = false;
  public boolean sitUpdate = false;
  public boolean isTeleporting = false;
  public boolean isKicked;
  public int kickCount = 0;
  private int id;
  private RoomTile startLocation;
  private RoomTile botStartLocation;
  private RoomTile previousLocation;
  private double previousLocationZ;
  private RoomTile currentLocation;
  private RoomTile goalLocation;
  private double z;
  private int tilesWalked;
  private boolean inRoom;
  private boolean canWalk;
  private boolean fastWalk = false;
  private boolean statusUpdate = false;
  private boolean invisible = false;
  private boolean canLeaveRoomByDoor = true;
  private RoomUserRotation bodyRotation = RoomUserRotation.NORTH;
  private RoomUserRotation headRotation = RoomUserRotation.NORTH;
  private DanceType danceType;
  private RoomUnitType roomUnitType;
  // Concurrent + volatile: the room cycle thread polls/clears this path while a
  // walk packet thread rebuilds it via findPath/setPath. A plain LinkedList would
  // corrupt under the concurrent structural modification.
  private volatile Deque<RoomTile> path = new ConcurrentLinkedDeque<>();
  private int handItem;
  private long handItemTimestamp;
  private long lastRollerTime;
  private long moveStatusTimestamp;
  private int walkTimeOut;
  private int effectId;
  private int effectEndTimestamp;
  private ScheduledFuture<?> moveBlockingTask;

  private int idleTimer;
  private Room room;
  private RoomRightLevels rightsLevel = RoomRightLevels.NONE;
  private THashSet<Integer> overridableTiles;

  public RoomUnit() {
    this.id = 0;
    this.inRoom = false;
    this.canWalk = true;
    this.status = new ConcurrentHashMap<>();
    this.cacheable = new THashMap<>();
    this.roomUnitType = RoomUnitType.UNKNOWN;
    this.danceType = DanceType.NONE;
    this.handItem = 0;
    this.handItemTimestamp = 0;
    this.walkTimeOut = Emulator.getIntUnixTimestamp();
    this.effectId = 0;
    this.isKicked = false;
    this.overridableTiles = new THashSet<>();
  }

  public void clearWalking() {
    this.goalLocation = null;
    this.startLocation = this.currentLocation;
    this.inRoom = false;
    this.moveStatusTimestamp = 0L;

    this.status.clear();

    this.cacheable.clear();
  }

  public void stopWalking() {
    synchronized (this.status) {
      this.status.remove(RoomUnitStatus.MOVE);
      this.setGoalLocation(this.currentLocation);
    }
  }

  public boolean cycle(Room room) {
    try {
      Habbo rider = null;
      if (this.getRoomUnitType() == RoomUnitType.PET) {
        Pet pet = room.getPet(this);
        if (pet instanceof RideablePet) {
          rider = ((RideablePet) pet).getRider();
        }
      }

      if (rider != null) {
        // copy things from rider
        if (this.status.containsKey(RoomUnitStatus.MOVE) && !rider.getRoomUnit().getStatusMap()
            .containsKey(RoomUnitStatus.MOVE)) {
          this.status.remove(RoomUnitStatus.MOVE);
        }

        if (rider.getRoomUnit().getCurrentLocation().x != this.getX()
            || rider.getRoomUnit().getCurrentLocation().y != this.getY()) {
          this.status.put(RoomUnitStatus.MOVE,
              rider.getRoomUnit().getCurrentLocation().x + "," + rider.getRoomUnit()
                  .getCurrentLocation().y + "," + (rider.getRoomUnit().getCurrentLocation()
                  .getStackHeight()));
          this.setPreviousLocation(rider.getRoomUnit().getPreviousLocation());
          this.setPreviousLocationZ(rider.getRoomUnit().getPreviousLocation().getStackHeight());
          this.setCurrentLocation(rider.getRoomUnit().getCurrentLocation());
          this.setZ(rider.getRoomUnit().getCurrentLocation().getStackHeight());
        }

        return this.statusUpdate;
      }

      if (!this.isWalking() && !this.isKicked) {
        if (this.status.remove(RoomUnitStatus.MOVE) == null) {
          Habbo habboT = room.getHabbo(this);
          if (habboT != null && habboT.getHabboInfo() != null && habboT.getHabboInfo().getRiding() != null) {
            RoomUnit ridingRoomUnit = habboT.getHabboInfo().getRiding().getRoomUnit();
            if (ridingRoomUnit != null) {
              ridingRoomUnit.status.remove(RoomUnitStatus.MOVE);
            }
          }
          return true;
        }
      }

      if (this.status.remove(RoomUnitStatus.SIT) != null) {
        this.statusUpdate = true;
      }
      if (this.status.remove(RoomUnitStatus.MOVE) != null) {
        this.statusUpdate = true;
      }
      if (this.status.remove(RoomUnitStatus.LAY) != null) {
        this.statusUpdate = true;
      }

      for (Map.Entry<RoomUnitStatus, String> set : this.status.entrySet()) {
        if (set.getKey().removeWhenWalking) {
          this.status.remove(set.getKey());
        }
      }

      if (this.path == null || this.path.isEmpty()) {
        return true;
      }

      boolean canfastwalk = true;
      Habbo habboT = room.getHabbo(this);
      if (habboT != null) {
        if (habboT.getHabboInfo().getRiding() != null) {
          canfastwalk = false;
        }
      }

      RoomTile next = this.path.poll();
      boolean overrideChecks = next != null && this.canOverrideTile(next);

      if (this.path.isEmpty()) {
        this.sitUpdate = true;

        if (next != null && next.hasUnits() && !overrideChecks) {
          return false;
        }
      }

      if (canfastwalk && this.fastWalk && this.path.size() > 1) {
        next = this.path.poll();
      }

      if (next == null) {
        return true;
      }

      Habbo habbo = room.getHabbo(this);

      this.status.remove(RoomUnitStatus.DEAD);

      if (habbo != null) {
        if (this.isIdle()) {
          UserIdleEvent event = new UserIdleEvent(habbo, UserIdleEvent.IdleReason.WALKED, false);
          Emulator.getPluginManager().fireEvent(event);

          if (!event.isCancelled()) {
            if (!event.idle) {
              room.unIdle(habbo);
              this.idleTimer = 0;
            }
          }
        }

        if (Emulator.getPluginManager().isRegistered(UserTakeStepEvent.class, false)) {
          Event e = new UserTakeStepEvent(habbo, room.getLayout().getTile(this.getX(), this.getY()),
              next);
          Emulator.getPluginManager().fireEvent(e);

          if (e.isCancelled()) {
            return true;
          }
        }
      }

      HabboItem item = room.getItemManager().getWalkableItemAt(next.x, next.y);
      boolean canSitNextTile = room.canSitAt(next.x, next.y);
      boolean canLayNextTile = room.canLayAt(next.x, next.y);

      if (!(this.path.isEmpty() && (canSitNextTile || canLayNextTile))) {
        double height = next.getStackHeight() - this.currentLocation.getStackHeight();
        if (canMoveToTile(room, next, height, canSitNextTile, canLayNextTile)) {
          this.path.clear();
          this.status.remove(RoomUnitStatus.MOVE);
          return false;
        }
      }

      if (canSitNextTile) {
        HabboItem tallestChair = room.getTallestChair(next);

        if (tallestChair != null) {
          item = tallestChair;
        }
      }

      if (next.equals(this.goalLocation) && (next.state == RoomTileState.SIT || next.state == RoomTileState.LAY) && !overrideChecks && (
          item == null || item.getZ() - this.getZ() > RoomLayout.MAXIMUM_STEP_HEIGHT)) {
        this.status.remove(RoomUnitStatus.MOVE);
        return false;
      }

      double zHeight = 0.0D;

            /*if (((habbo != null && habbo.getHabboInfo().getRiding() != null) || isRiding) && next.equals(this.goalLocation) && (next.state == RoomTileState.SIT || next.state == RoomTileState.LAY)) {
                this.status.remove(RoomUnitStatus.MOVE);
                return false;
            }*/

      if (habbo != null) {
        if (habbo.getHabboInfo().getRiding() != null) {
          zHeight += 1.0D;
        }
      }

      HabboItem habboItem = room.getTopItemAt(this.getX(), this.getY());
      if (habboItem != null) {
        if (habboItem != item || !RoomLayout.pointInSquare(habboItem.getX(), habboItem.getY(),
            habboItem.getX() + habboItem.getBaseItem().getWidth() - 1,
            habboItem.getY() + habboItem.getBaseItem().getLength() - 1, next.x, next.y)) {
          habboItem.onWalkOff(this, room, new Object[]{this.getCurrentLocation(), next});
        }
      }

      this.tilesWalked++;

      RoomUserRotation oldRotation = this.getBodyRotation();
      this.setRotation(
          RoomUserRotation.values()[Rotation.Calculate(this.getX(), this.getY(), next.x, next.y)]);
      if (item != null) {
        if (item != habboItem || !RoomLayout.pointInSquare(item.getX(), item.getY(),
            item.getX() + item.getBaseItem().getWidth() - 1,
            item.getY() + item.getBaseItem().getLength() - 1, this.getX(), this.getY())) {
          if (item.canWalkOn(this, room, null)) {
            item.onWalkOn(this, room, new Object[]{this.getCurrentLocation(), next});
          } else if (item instanceof ConditionalGate) {
            this.setRotation(oldRotation);
            this.tilesWalked--;
            this.setGoalLocation(this.currentLocation);
            this.status.remove(RoomUnitStatus.MOVE);
            room.sendComposer(new RoomUserStatusComposer(this).compose());

            if (habbo != null) {
              ((ConditionalGate) item).onRejected(this, this.getRoom(), new Object[]{});
            }
            return false;
          }
        } else {
          item.onWalk(this, room, new Object[]{this.getCurrentLocation(), next});
        }

        zHeight += item.getZ();

        if (!item.getBaseItem().allowSit() && !item.getBaseItem().allowLay()) {
          zHeight += Item.getCurrentHeight(item);
        }
      } else {
        zHeight += room.getLayout().getHeightAtSquare(next.x, next.y);
      }

      Optional<HabboItem> stackHelper = room.getItemsAt(next).stream()
          .filter(i -> i instanceof InteractionTileWalkMagic || i instanceof InteractionStackWalkHelper).findAny();
      if (stackHelper.isPresent()) {
        zHeight = stackHelper.get().getZ();
      }

      this.setPreviousLocation(this.getCurrentLocation());

      this.setStatus(RoomUnitStatus.MOVE, next.x + "," + next.y + "," + zHeight);
      if (habbo != null) {
        if (habbo.getHabboInfo().getRiding() != null) {
          RoomUnit ridingUnit = habbo.getHabboInfo().getRiding().getRoomUnit();

          if (ridingUnit != null) {
            ridingUnit.setPreviousLocationZ(this.getZ());
            this.setZ(zHeight - 1.0);
            ridingUnit.setRotation(
                RoomUserRotation.values()[Rotation.Calculate(this.getX(), this.getY(), next.x,
                    next.y)]);
            ridingUnit.setPreviousLocation(this.getCurrentLocation());
            ridingUnit.setGoalLocation(this.getGoal());
            ridingUnit.setStatus(RoomUnitStatus.MOVE,
                next.x + "," + next.y + "," + (zHeight - 1.0));
            room.sendComposer(new RoomUserStatusComposer(ridingUnit).compose());
            //ridingUnit.setZ(zHeight - 1.0);
          }
        }
      }
      //room.sendComposer(new RoomUserStatusComposer(this).compose());

      this.setZ(zHeight);
      this.setCurrentLocation(room.getLayout().getTile(next.x, next.y));
      this.resetIdleTimer();

      if (habbo != null) {
        HabboItem topItem = room.getTopItemAt(next.x, next.y);

        boolean isAtDoor =
            next.x == room.getLayout().getDoorX() && next.y == room.getLayout().getDoorY();
        boolean publicRoomKicks = !room.isPublicRoom() || Emulator.getConfig()
            .getBoolean("hotel.room.public.doortile.kick");
        boolean invalidated = topItem != null && topItem.invalidatesToRoomKick();

        if (this.canLeaveRoomByDoor && isAtDoor && publicRoomKicks && !invalidated) {
          Emulator.getThreading().run(new RoomUnitKick(habbo, room, false), 500);
        }
      }

      return false;

    } catch (Exception e) {
      LOGGER.error("Caught exception", e);
      return false;
    }
  }

  private static boolean canMoveToTile(Room room, RoomTile next, double height,
      boolean canSitNextTile, boolean canLayNextTile) {
    return (!room.tileWalkable(next) || (!RoomLayout.ALLOW_FALLING
        && height < -RoomLayout.MAXIMUM_STEP_HEIGHT) || (next.state == RoomTileState.OPEN
        && height > RoomLayout.MAXIMUM_STEP_HEIGHT)) && !canSitNextTile && !canLayNextTile;
  }

  public int getId() {
    return this.id;
  }

  public void setId(int id) {
    this.id = id;
  }

  public RoomTile getCurrentLocation() {
    return this.currentLocation;
  }

  public void setCurrentLocation(RoomTile location) {
    if (location != null) {
      if (this.currentLocation != null) {
        this.currentLocation.removeUnit(this);
      }
      this.currentLocation = location;
      location.addUnit(this);
    }
  }

  public short getX() {
    return this.currentLocation == null ? 0 : this.currentLocation.x;
  }

  public short getY() {
    return this.currentLocation == null ? 0 : this.currentLocation.y;
  }

  public double getZ() {
    return this.z;
  }

  public void setZ(double z) {
    this.z = z;

    if (this.room != null) {
      Bot bot = this.room.getBot(this);
      if (bot != null) {
        bot.needsUpdate(true);
      }
    }
  }

  public boolean isInRoom() {
    return this.inRoom;
  }

  public synchronized void setInRoom(boolean inRoom) {
    this.inRoom = inRoom;
  }

  public RoomUnitType getRoomUnitType() {
    return this.roomUnitType;
  }

  public synchronized void setRoomUnitType(RoomUnitType roomUnitType) {
    this.roomUnitType = roomUnitType;
  }

  public void setRotation(RoomUserRotation rotation) {
    this.bodyRotation = rotation;
    this.headRotation = rotation;
  }

  public RoomUserRotation getBodyRotation() {
    return this.bodyRotation;
  }

  public void setBodyRotation(RoomUserRotation bodyRotation) {
    this.bodyRotation = bodyRotation;
  }

  public RoomUserRotation getHeadRotation() {
    return this.headRotation;
  }

  public void setHeadRotation(RoomUserRotation headRotation) {
    this.headRotation = headRotation;
  }

  public DanceType getDanceType() {
    return this.danceType;
  }

  public synchronized void setDanceType(DanceType danceType) {
    this.danceType = danceType;
  }

  public void setCanWalk(boolean value) {
    this.canWalk = value;
  }

  public boolean canWalk() {
    return this.canWalk;
  }

  public boolean isFastWalk() {
    return this.fastWalk;
  }

  public void setFastWalk(boolean fastWalk) {
    this.fastWalk = fastWalk;
  }

  public RoomTile getStartLocation() {
    return this.startLocation;
  }

  public int tilesWalked() {
    return this.tilesWalked;
  }

  public RoomTile getGoal() {
    return this.goalLocation;
  }

  public void setGoalLocation(RoomTile goalLocation) {
    if (goalLocation != null) {
      //      if (goalLocation.state != RoomTileState.INVALID) {
      this.setGoalLocation(goalLocation, false);
    }
    //}
  }

  public void setGoalLocation(RoomTile goalLocation, boolean noReset) {
    if (Emulator.getPluginManager().isRegistered(RoomUnitSetGoalEvent.class, false)) {
      Event event = new RoomUnitSetGoalEvent(this.room, this, goalLocation);
      Emulator.getPluginManager().fireEvent(event);

      if (event.isCancelled()) {
        return;
      }
    }

    /// Set start location
    this.startLocation = this.currentLocation;

    if (goalLocation != null && !noReset) {
      boolean isWalking = this.hasStatus(RoomUnitStatus.MOVE);
      this.goalLocation = goalLocation;
      this.findPath(); ///< Quadral: this is where we start formulating a path
      if (!this.path.isEmpty()) {
        this.tilesWalked = isWalking ? this.tilesWalked : 0;
        this.cmdSit = false;
      } else {
        this.goalLocation = this.currentLocation;
      }
    }
  }

  public void setLocation(RoomTile location) {
    if (location != null) {
      this.startLocation = location;
      setPreviousLocation(location);
      setCurrentLocation(location);
      this.goalLocation = location;
      this.botStartLocation = location;
    }
  }

  public RoomTile getBotStartLocation() {
    return this.botStartLocation;
  }

  public void setBotStartLocation(RoomTile botStartLocation) {
    this.botStartLocation = botStartLocation;
  }

  public RoomTile getPreviousLocation() {
    return this.previousLocation;
  }

  public void setPreviousLocation(RoomTile previousLocation) {
    this.previousLocation = previousLocation;
    this.previousLocationZ = this.z;
  }

  public double getPreviousLocationZ() {
    return this.previousLocationZ;
  }

  public void setPreviousLocationZ(double z) {
    this.previousLocationZ = z;
  }

  public void setPathFinderRoom(Room room) {
    this.room = room;
  }

  public void findPath() {
    if (!canFindPath()) {
      return;
    }

    Deque<RoomTile> newPath = this.room.getLayout().getPathfinder()
        .findPath(this.currentLocation, this.goalLocation, this.goalLocation, this);
    if (newPath != null && !newPath.isEmpty()) {
      this.path = new ConcurrentLinkedDeque<>(newPath);
    }
  }

  private boolean canFindPath() {
    return this.room != null && this.room.getLayout() != null && this.goalLocation != null && (
        this.goalLocation.isWalkable() || this.room.canSitOrLayAt(this.goalLocation.x,
            this.goalLocation.y) || this.canOverrideTile(this.goalLocation));
  }

  public boolean isAtGoal() {
    if (this.currentLocation == null) return true;
    return this.currentLocation.equals(this.goalLocation);
  }

  public boolean isWalking() {
    return !this.isAtGoal() && this.canWalk;
  }

  public String getStatus(RoomUnitStatus key) {
    return this.status.get(key);
  }

  public ConcurrentHashMap<RoomUnitStatus, String> getStatusMap() {
    return this.status;
  }

  public void removeStatus(RoomUnitStatus key) {
    if (key == RoomUnitStatus.MOVE) {
      this.moveStatusTimestamp = 0L;
    }
    this.status.remove(key);
  }

  public void setStatus(RoomUnitStatus key, String value) {
    if (key != null && value != null) {
      if (key == RoomUnitStatus.MOVE) {
        this.moveStatusTimestamp = System.currentTimeMillis();
        WiredMoveCarryHelper.clearStatusComposerSuppression(this);
        WiredUserMovementHelper.clearStatusComposerSuppression(this);
      }

      this.status.put(key, value);
    }
  }

  public boolean hasStatus(RoomUnitStatus key) {
    return this.status.containsKey(key);
  }

  public void clearStatus() {
    this.moveStatusTimestamp = 0L;
    this.status.clear();
  }

  public void statusUpdate(boolean update) {
    this.statusUpdate = update;
  }

  public boolean needsStatusUpdate() {
    return this.statusUpdate;
  }

  public TMap<String, Object> getCacheable() {
    return this.cacheable;
  }

  public int getHandItem() {
    return this.handItem;
  }

  public long getLastRollerTime() {
    return this.lastRollerTime;
  }

  public void setLastRollerTime(long lastRollerTime) {
    this.lastRollerTime = lastRollerTime;
  }

  public long getMoveStatusTimestamp() {
    return this.moveStatusTimestamp;
  }

  /**
   * Checks if enough time has passed since the last roller movement to allow rolling again.
   * This prevents desync issues where the client hasn't finished the roller animation.
   * @return true if the unit can be rolled, false if still in roller cooldown
   */
  public boolean canBeRolled() {
    return System.currentTimeMillis() - this.lastRollerTime >= 480;
  }

  public void setHandItem(int handItem) {
    this.handItem = handItem;
    this.handItemTimestamp = System.currentTimeMillis();
  }

  public long getHandItemTimestamp() {
    return this.handItemTimestamp;
  }

  public int getEffectId() {
    return this.effectId;
  }

  public void setEffectId(int effectId, int endTimestamp) {
    this.effectId = effectId;
    this.effectEndTimestamp = endTimestamp;
  }

  public int getEffectEndTimestamp() {
    return this.effectEndTimestamp;
  }

  public int getWalkTimeOut() {
    return this.walkTimeOut;
  }

  public void setWalkTimeOut(int walkTimeOut) {
    this.walkTimeOut = walkTimeOut;
  }

  public void increaseIdleTimer() {
    this.idleTimer++;
  }

  public boolean isIdle() {
    return this.idleTimer > Room.IDLE_CYCLES; //Amount of room cycles / 2 = seconds.
  }

  public int getIdleTimer() {
    return this.idleTimer;
  }

  public void resetIdleTimer() {
    this.idleTimer = 0;
  }

  public void setIdle() {
    this.idleTimer = Room.IDLE_CYCLES + 1;
  }

  public void lookAtPoint(RoomTile location) {
    if (!this.canRotate) {
      return;
    }

    if (Emulator.getPluginManager().isRegistered(RoomUnitLookAtPointEvent.class, false)) {
      Event lookAtPointEvent = new RoomUnitLookAtPointEvent(this.room, this, location);
      Emulator.getPluginManager().fireEvent(lookAtPointEvent);

      if (lookAtPointEvent.isCancelled()) {
        return;
      }
    }

    if (this.status.containsKey(RoomUnitStatus.LAY)) {
      return;
    }

    if (!this.status.containsKey(RoomUnitStatus.SIT)) {
      this.bodyRotation = (RoomUserRotation.values()[Rotation.Calculate(this.getX(), this.getY(),
          location.x, location.y)]);
    }

    RoomUserRotation rotation = (RoomUserRotation.values()[Rotation.Calculate(this.getX(),
        this.getY(), location.x, location.y)]);

    if (Math.abs(rotation.getValue() - this.bodyRotation.getValue()) <= 1) {
      this.headRotation = rotation;
    }
  }

  public Deque<RoomTile> getPath() {
    return this.path;
  }

  public void setPath(Deque<RoomTile> path) {
    this.path = (path == null) ? new ConcurrentLinkedDeque<>() : new ConcurrentLinkedDeque<>(path);
  }

  public RoomRightLevels getRightsLevel() {
    return this.rightsLevel;
  }

  public void setRightsLevel(RoomRightLevels rightsLevel) {
    this.rightsLevel = rightsLevel;
  }

  public boolean isInvisible() {
    return this.invisible;
  }

  public void setInvisible(boolean invisible) {
    this.invisible = invisible;
  }

  public Room getRoom() {
    return room;
  }

  public void setRoom(Room room) {
    this.room = room;
  }

  public boolean canOverrideTile(RoomTile tile) {
    if (tile == null || room == null || room.getLayout() == null) {
      return false;
    }

    if (room.getItemsAt(tile).stream().anyMatch(i -> i.canOverrideTile(this, room, tile))) {
      return true;
    }

    int tileIndex = (tile.x & 0xFF) | (tile.y << 12);
    return this.overridableTiles.contains(tileIndex);
  }

  public void addOverrideTile(RoomTile tile) {
    int tileIndex = (tile.x & 0xFF) | (tile.y << 12);
    if (!this.overridableTiles.contains(tileIndex)) {
      this.overridableTiles.add(tileIndex);
    }
  }

  public void removeOverrideTile(RoomTile tile) {
    if (room == null || room.getLayout() == null) {
      return;
    }

    int tileIndex = (tile.x & 0xFF) | (tile.y << 12);
    this.overridableTiles.remove(tileIndex);
  }

  public void clearOverrideTiles() {
    this.overridableTiles.clear();
  }

  public boolean canLeaveRoomByDoor() {
    return canLeaveRoomByDoor;
  }

  public void setCanLeaveRoomByDoor(boolean canLeaveRoomByDoor) {
    this.canLeaveRoomByDoor = canLeaveRoomByDoor;
  }

  public boolean canForcePosture() {
    if (this.room == null) {
      return false;
    }

    HabboItem topItem = this.room.getTopItemAt(this.getX(), this.getY());

    return topItem == null || (!(topItem instanceof InteractionWater)
        && !(topItem instanceof InteractionWaterItem));
  }

  public RoomTile getClosestTile(List<RoomTile> tiles) {
    return tiles.stream()
        .min(Comparator.comparingDouble(a -> a.distance(this.getCurrentLocation()))).orElse(null);
  }

  public RoomTile getClosestAdjacentTile(short x, short y, boolean diagonal) {
    if (room == null) {
      return null;
    }

    RoomTile baseTile = room.getLayout().getTile(x, y);

    if (baseTile == null) {
      return null;
    }

    List<Integer> rotations = new ArrayList<>();
    rotations.add(RoomUserRotation.SOUTH.getValue());
    rotations.add(RoomUserRotation.NORTH.getValue());
    rotations.add(RoomUserRotation.EAST.getValue());
    rotations.add(RoomUserRotation.WEST.getValue());

    if (diagonal) {
      rotations.add(RoomUserRotation.NORTH_EAST.getValue());
      rotations.add(RoomUserRotation.NORTH_WEST.getValue());
      rotations.add(RoomUserRotation.SOUTH_EAST.getValue());
      rotations.add(RoomUserRotation.SOUTH_WEST.getValue());
    }

    return this.getClosestTile(
        rotations.stream().map(rotation -> room.getLayout().getTileInFront(baseTile, rotation))
            .filter(t -> t != null && t.isWalkable() && (this.getCurrentLocation().equals(t)
                || !room.hasHabbosAt(t.x, t.y))).collect(Collectors.toList()));
  }

  public ScheduledFuture<?> getMoveBlockingTask() {
    return moveBlockingTask;
  }

  public void setMoveBlockingTask(ScheduledFuture<?> moveBlockingTask) {
    this.moveBlockingTask = moveBlockingTask;
  }
}
