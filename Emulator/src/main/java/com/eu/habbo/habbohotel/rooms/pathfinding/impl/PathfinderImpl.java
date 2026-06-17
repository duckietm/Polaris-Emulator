package com.eu.habbo.habbohotel.rooms.pathfinding.impl;

import static com.eu.habbo.habbohotel.rooms.pathfinding.impl.PathfinderConstants.CONFIG_EXECUTION_TIME;
import static com.eu.habbo.habbohotel.rooms.pathfinding.impl.PathfinderConstants.CONFIG_TIMEOUT_ENABLED;
import static com.eu.habbo.habbohotel.rooms.pathfinding.impl.PathfinderConstants.TIMEOUT_CHECK_INTERVAL;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.pathfinding.Pathfinder;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class PathfinderImpl implements Pathfinder {

  private static final int CACHED_TIMEOUT_MS = Emulator.getConfig()
      .getInt(CONFIG_EXECUTION_TIME, 25);
  // Default ON: bound A* to CACHED_TIMEOUT_MS (25ms) so a pathological search
  // can't run unbounded and stall the thread. On timeout findPath returns an
  // empty path (the unit simply doesn't move there) — graceful degradation.
  private static final boolean CACHED_TIMEOUT_ENABLED = Emulator.getConfig()
      .getBoolean(CONFIG_TIMEOUT_ENABLED, true);
  private static final long CACHED_TIMEOUT_NANOS = CACHED_TIMEOUT_MS * 1_000_000L;

  private final Room room;
  private double maximumStepHeight;
  private boolean allowFalling;
  private final boolean retroStyleDiagonals;

  public PathfinderImpl(Room room, double maximumStepHeight, boolean allowFalling,
      boolean retroStyleDiagonals) {
    this.room = room;
    this.maximumStepHeight = maximumStepHeight;
    this.allowFalling = allowFalling;
    this.retroStyleDiagonals = retroStyleDiagonals;
  }


  @Override
  public CompletableFuture<Deque<RoomTile>> findPathAsync(RoomTile oldTile, RoomTile newTile,
      RoomTile goalLocation, RoomUnit roomUnit) {
    return CompletableFuture.supplyAsync(() -> findPath(oldTile, newTile, goalLocation, roomUnit))
        .exceptionally(error -> {
          throw new RuntimeException(new PathFinderException("Failed to find path", error));
        });
  }


  @Override
  public Deque<RoomTile> findPath(RoomTile oldTile, RoomTile newTile, RoomTile goalLocation,
      RoomUnit roomUnit) {
    return this.findPath(oldTile, newTile, goalLocation, roomUnit, false);
  }

  private boolean processCurrent(PathfinderContext context, RoomTile current,
      PriorityQueue<RoomTile> openList, HashSet<RoomTile> closedList) {
    if (current.getX() == context.getNewTile().getX() && current.getY() == context.getNewTile()
        .getY()) {
      return true;
    }

    TileValidator.swapList(current, closedList, openList);

    Set<RoomTile> adjacentNodes = AdjacentTileFinder.getAdjacent(context, current,
        context.getNewTile(), context.getRoomUnit(), context.isCanMoveDiagonally(),
        retroStyleDiagonals);
    adjacentNodes.forEach(
        currentAdj -> processAdjacent(context, currentAdj, closedList, current, openList));
    return false;
  }

  private void processAdjacent(PathfinderContext context, RoomTile currentAdj,
      HashSet<RoomTile> closedList, RoomTile current, PriorityQueue<RoomTile> openList) {
    if (closedList.contains(currentAdj)) {
      return;
    }

    if (context.getRoomUnit().canOverrideTile(currentAdj)) {
      AdjacentTileFinder.updateAdj(context, currentAdj, current, openList);
      return;
    }

    if (currentAdj.getState() == RoomTileState.BLOCKED || (
        (currentAdj.getState() == RoomTileState.SIT || currentAdj.getState() == RoomTileState.LAY)
            && !currentAdj.equals(context.getGoalLocation()))) {
      TileValidator.swapList(currentAdj, closedList, openList);
      return;
    }

    if (isInvalidHeight(context, currentAdj, current) || TileValidator.isAnyUnitAt(this.room,
        context, currentAdj, closedList, openList)) {
      return;
    }

    AdjacentTileFinder.calculateCost(context, currentAdj, current, openList);
  }

  @Override
  public Deque<RoomTile> findPath(RoomTile oldTile, RoomTile newTile, RoomTile goalLocation,
      RoomUnit roomUnit, boolean isWalkthroughRetry) {
    if (this.room == null || !this.room.isLoaded() || oldTile == null || newTile == null
        || oldTile.equals(newTile) || newTile.getState() == RoomTileState.INVALID) {
      return new LinkedList<>();
    }
    long startTime = CACHED_TIMEOUT_ENABLED ? System.nanoTime() : 0;
    int iterationCount = 0;

    PriorityQueue<RoomTile> openList = new PriorityQueue<>(
        Comparator.comparingInt(RoomTile::getfCosts));
    HashSet<RoomTile> closedList = new HashSet<>();

    openList.add(oldTile.copy());
    PathfinderContext context = PathfinderContext.buildContext(this.room, newTile, goalLocation,
        roomUnit, isWalkthroughRetry);

    try {
      while (!openList.isEmpty()) {
        if (CACHED_TIMEOUT_ENABLED && (++iterationCount & (TIMEOUT_CHECK_INTERVAL - 1)) == 0
            && System.nanoTime() - startTime > CACHED_TIMEOUT_NANOS) {
          return new LinkedList<>();
        }

        RoomTile current = openList.poll();
        if (current == null) {
          break;
        }
        if (processCurrent(context, current, openList, closedList)) {
          return this.tracePath(
              AdjacentTileFinder.findTile(context, oldTile.getX(), oldTile.getY()), current);
        }
      }

      if (context.isAllowWalkthrough() && !isWalkthroughRetry) {
        return this.findPath(oldTile, newTile, goalLocation, roomUnit, true);
      }

      return new LinkedList<>();
    } finally {
      // Optional: Clear collections for immediate memory release
      // (GC will handle this anyway, but clearing can help in high-frequency scenarios)
      openList.clear();
      closedList.clear();
      if (context.getTileCache() != null) {
        context.getTileCache().clear();
      }
    }
  }


  private boolean isInvalidHeight(PathfinderContext context, RoomTile currentAdj,
      RoomTile current) {
    double height = currentAdj.getStackHeight() - current.getStackHeight();
    return (!this.allowFalling && height < -this.maximumStepHeight)
        || (currentAdj.getState() == RoomTileState.OPEN && height > this.maximumStepHeight)
        && (findPathAroundAdjacentTile(context, currentAdj, current, height));
  }

  /**
   * Check for intermediate tiles with smaller height differences
   *
   * @param context    The pathfinder context
   * @param currentAdj The current adjacent tile
   * @param current    The current tile
   * @param height     The height difference
   * @return True if the path is around the current adjacent tile, false otherwise
   */
  private boolean findPathAroundAdjacentTile(PathfinderContext context, RoomTile currentAdj,
      RoomTile current, double height) {
    PriorityQueue<RoomTile> adjacentTiles = new PriorityQueue<>(Comparator.comparingDouble(
        tile -> Math.abs(tile.getStackHeight() - current.getStackHeight())));

    adjacentTiles.addAll(AdjacentTileFinder.getAdjacent(context, current, context.getNewTile(),
        context.getRoomUnit(), context.isCanMoveDiagonally(), retroStyleDiagonals));
    RoomTile intermediateTile = adjacentTiles.peek();

    if (intermediateTile == null || Math.abs(height) > this.maximumStepHeight) {
      return true;
    }

    currentAdj.setPrevious(intermediateTile);
    intermediateTile.setPrevious(current);
    return false;
  }


  /**
   * Traces the path from the goal tile to the start tile by following the 'previous' references in
   * the tiles. The traced path is returned as a deque where each element represents a tile on the
   * path.
   *
   * @param start The starting tile of the path.
   * @param goal  The goal tile where the path tracing begins.
   * @return A deque of RoomTile objects representing the traced path from the goal to the start. If
   * the start tile is null or the path cannot be traced, an empty deque is returned.
   */
  public Deque<RoomTile> tracePath(RoomTile start, RoomTile goal) {
    Deque<RoomTile> path = new ArrayDeque<>();
    RoomLayout layout = this.room.getLayout();
    if (start == null) {
      return path;
    }

    RoomTile curr = goal;
    while (curr != null) {
      path.addFirst(layout.getTile(curr.getX(), curr.getY()));
      curr = curr.getPrevious();
      if ((curr != null) && (curr.equals(start))) {
        return path;
      }
    }
    return path;
  }


  @Override
  public boolean isAllowFalling() {
    return this.allowFalling;
  }

  @Override
  public void setAllowFalling(boolean allow) {
    this.allowFalling = allow;
  }

  @Override
  public double getMaxStepHeight() {
    return this.maximumStepHeight;
  }

  @Override
  public void setMaxStepHeight(double value) {
    this.maximumStepHeight = value;
  }
}