package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.rooms.FurnitureMovementError;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.users.HabboItem;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks simulated room state changes for pre-validation of wired movement effects.
 * <p>
 * When the Movement Validation condition is present, movement effects are first simulated
 * to verify all moves can complete successfully. This class tracks item position changes
 * without modifying the actual room. If all movements succeed in simulation, they are
 * then executed for real.
 * </p>
 * 
 * <h3>Key Feature:</h3>
 * <p>
 * The simulation tracks cumulative position changes. If Effect 1 moves an item from
 * tile 0 to tile 1, and Effect 2 moves it forward again, the simulation knows the
 * item is now at tile 1 (not tile 0) and validates the move to tile 2 correctly.
 * </p>
 * 
 * <h3>Usage:</h3>
 * <pre>{@code
 * WiredSimulation sim = new WiredSimulation(room);
 * 
 * // Movement effects call simulation methods
 * if (!sim.moveItem(item, newX, newY, newZ, rotation)) {
 *     // Move would fail - simulation is now marked as failed
 * }
 * 
 * // Check if all simulated moves succeeded
 * if (!sim.hasFailed()) {
 *     // Safe to execute real moves
 * }
 * }</pre>
 */
public final class WiredSimulation {
    
    private final Room room;
    private final Map<Integer, SimulatedPosition> itemPositions;
    private boolean failed;
    private String failureReason;
    
    public WiredSimulation(Room room) {
        this.room = room;
        this.itemPositions = new HashMap<>();
        this.failed = false;
        this.failureReason = null;
    }
    
    public Room getRoom() {
        return room;
    }
    
    public boolean hasFailed() {
        return failed;
    }
    
    public String getFailureReason() {
        return failureReason;
    }
    
    public void fail(String reason) {
        this.failed = true;
        this.failureReason = reason;
    }
    
    /**
     * Simulate moving an item to a new position.
     * Validates the destination tile using the room's furnitureFitsAt method,
     * which checks for users, bots, pets, other furniture, and tile state.
     * 
     * @param item the item to move
     * @param newX new X coordinate
     * @param newY new Y coordinate
     * @param newZ new Z height
     * @param newRotation new rotation
     * @return true if the move would succeed, false if it would fail
     */
    public boolean moveItem(HabboItem item, short newX, short newY, double newZ, int newRotation) {
        if (item == null) {
            fail("Cannot move null item");
            return false;
        }
        
        if (room.getLayout() == null) {
            fail("Room has no layout");
            return false;
        }
        
        RoomTile destTile = room.getLayout().getTile(newX, newY);
        if (destTile == null) {
            fail("Destination tile (" + newX + "," + newY + ") does not exist");
            return false;
        }
        
        if (destTile.getState() == RoomTileState.INVALID) {
            fail("Destination tile (" + newX + "," + newY + ") is invalid/hole");
            return false;
        }
        
        FurnitureMovementError moveError = room.furnitureFitsAt(destTile, item, newRotation, true);
        if (moveError != FurnitureMovementError.NONE) {
            fail("Destination tile (" + newX + "," + newY + ") blocked: " + moveError.name());
            return false;
        }
        
        itemPositions.put(item.getId(), new SimulatedPosition(newX, newY, newZ, newRotation));
        return true;
    }
    
    /**
     * Simulate moving an item by a relative offset.
     */
    public boolean moveItemRelative(HabboItem item, int offsetX, int offsetY) {
        if (item == null) {
            fail("Cannot move null item");
            return false;
        }
        
        SimulatedPosition currentPos = getItemPosition(item);
        short newX = (short) (currentPos.x + offsetX);
        short newY = (short) (currentPos.y + offsetY);
        
        return moveItem(item, newX, newY, currentPos.z, currentPos.rotation);
    }
    
    /**
     * Get the current (possibly simulated) position of an item.
     * Returns simulated position if one exists, otherwise the real position.
     */
    public SimulatedPosition getItemPosition(HabboItem item) {
        if (item == null) {
            return null;
        }
        
        SimulatedPosition simPos = itemPositions.get(item.getId());
        if (simPos != null) {
            return simPos;
        }
        
        return new SimulatedPosition(item.getX(), item.getY(), item.getZ(), item.getRotation());
    }
    
    /**
     * Check if a tile is valid for an item to move to.
     * Uses furnitureFitsAt for full validation including users, bots, furniture, etc.
     */
    public boolean isTileValidForItem(short x, short y, HabboItem item) {
        if (room.getLayout() == null) {
            return false;
        }
        
        RoomTile tile = room.getLayout().getTile(x, y);
        if (tile == null) {
            return false;
        }
        if (tile.getState() == RoomTileState.INVALID) {
            return false;
        }
        
        FurnitureMovementError moveError = room.furnitureFitsAt(tile, item, item.getRotation(), true);
        return moveError == FurnitureMovementError.NONE;
    }
    
    /**
     * Reset the simulation state for reuse.
     */
    public void reset() {
        itemPositions.clear();
        failed = false;
        failureReason = null;
    }
    
    /**
     * Represents a simulated item position.
     */
    public static final class SimulatedPosition {
        public final short x;
        public final short y;
        public final double z;
        public final int rotation;
        
        public SimulatedPosition(short x, short y, double z, int rotation) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.rotation = rotation;
        }
        
        @Override
        public String toString() {
            return "SimPos{x=" + x + ", y=" + y + ", z=" + z + ", rot=" + rotation + "}";
        }
    }
}
