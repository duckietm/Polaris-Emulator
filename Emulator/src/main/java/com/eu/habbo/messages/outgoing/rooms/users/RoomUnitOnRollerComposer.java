package com.eu.habbo.messages.outgoing.rooms.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.items.interactions.InteractionRoller;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.pets.RideablePet;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomQueueSpeedControlSupport;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitType;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RoomUnitOnRollerComposer extends MessageComposer {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomUnitOnRollerComposer.class);
    private final RoomUnit roomUnit;
    private final HabboItem roller;
    private final RoomTile oldLocation;
    private final double oldZ;
    private final RoomTile newLocation;
    private final double newZ;
    private final Room room;
    private int x;
    private int y;
    private HabboItem oldTopItem;

    public RoomUnitOnRollerComposer(RoomUnit roomUnit, HabboItem roller, RoomTile oldLocation, double oldZ, RoomTile newLocation, double newZ, Room room) {
        this.roomUnit = roomUnit;
        this.roller = roller;
        this.oldLocation = oldLocation;
        this.oldZ = oldZ;
        this.newLocation = newLocation;
        this.newZ = newZ;
        this.room = room;
        oldTopItem = this.room.getTopItemAt(oldLocation.x, oldLocation.y);
    }

    public RoomUnitOnRollerComposer(RoomUnit roomUnit, RoomTile newLocation, Room room) {
        this.roomUnit = roomUnit;
        this.roller = null;
        this.oldLocation = this.roomUnit.getCurrentLocation();
        this.oldZ = this.roomUnit.getZ();
        this.newLocation = newLocation;
        this.newZ = this.newLocation.getStackHeight();
        this.room = room;
        this.oldTopItem = null;
    }

    @Override
    protected ServerMessage composeInternal() {
        if (!this.room.isLoaded())
            return null;

        // Early validation: Check if the roller movement is still valid before composing the packet
        if (this.roller != null && room.getLayout() != null) {
            // Check if the destination tile is blocked by another unit that moved there
            // Skip this check for pets with riders or users riding pets (they share the tile)
            boolean isRidingPetOrRider = false;
            if (this.roomUnit.getRoomUnitType() == RoomUnitType.PET) {
                Pet pet = this.room.getPet(this.roomUnit);
                if (pet instanceof RideablePet rideablePet && rideablePet.getRider() != null) {
                    isRidingPetOrRider = true;
                }
            } else if (this.roomUnit.getRoomUnitType() == RoomUnitType.USER) {
                Habbo habbo = this.room.getHabbo(this.roomUnit);
                if (habbo != null && habbo.getHabboInfo() != null && habbo.getHabboInfo().getRiding() != null) {
                    isRidingPetOrRider = true;
                }
            }
            
            if (!isRidingPetOrRider && this.newLocation.hasUnits() && !this.newLocation.getUnits().contains(this.roomUnit)) {
                return null;
            }
            
            // Check if the unit is still at the expected old location (they might have walked away)
            if (this.roomUnit.getCurrentLocation() != this.oldLocation) {
                return null;
            }
            
            // Check if the unit started walking (user input should take priority over rollers)
            if (this.roomUnit.isWalking()) {
                return null;
            }
        }

        this.response.init(Outgoing.ObjectOnRollerComposer);
        this.response.appendInt(this.oldLocation.x);
        this.response.appendInt(this.oldLocation.y);
        this.response.appendInt(this.newLocation.x);
        this.response.appendInt(this.newLocation.y);
        this.response.appendInt(0);
        this.response.appendInt(this.roller == null ? 0 : this.roller.getId());
        this.response.appendInt(2);
        this.response.appendInt(this.roomUnit.getId());
        this.response.appendString(this.oldZ + "");
        this.response.appendString(this.newZ + "");

        if (this.roller != null && room.getLayout() != null) {
            // Mark the unit as recently rolled to prevent desync/bungie effect
            this.roomUnit.setLastRollerTime(System.currentTimeMillis());
            
            // Update location immediately to prevent desync issues where the unit gets 
            // "stuck" rolling because subsequent roller cycles see the unit at the old position
            if (!this.roomUnit.isWalking() && this.roomUnit.getCurrentLocation() == this.oldLocation) {
                this.roomUnit.setLocation(this.newLocation);
                this.roomUnit.setZ(this.newZ);
                this.roomUnit.setPreviousLocationZ(this.newZ);
                
                // Mark bots and pets for database update when moved by rollers
                if (this.roomUnit.getRoomUnitType() == RoomUnitType.BOT) {
                    Bot bot = this.room.getBot(this.roomUnit);
                    if (bot != null) {
                        bot.needsUpdate(true);
                    }
                } else if (this.roomUnit.getRoomUnitType() == RoomUnitType.PET) {
                    Pet pet = this.room.getPet(this.roomUnit);
                    if (pet != null) {
                        pet.needsUpdate = true;
                        
                        // If this pet has a rider, sync the rider's position with the pet
                        if (pet instanceof RideablePet rideablePet) {
                            Habbo rider = rideablePet.getRider();
                            if (rider != null && rider.getRoomUnit() != null) {
                                rider.getRoomUnit().setLastRollerTime(System.currentTimeMillis());
                            }
                        }
                    }
                }
            }
            
            // Delay the walk on/off events to allow the visual animation to complete
            Emulator.getThreading().run(() -> {
                if (!this.roomUnit.isWalking()) {
                    HabboItem topItem = this.room.getTopItemAt(this.oldLocation.x, this.oldLocation.y);
                    HabboItem topItemNewLocation = this.room.getTopItemAt(this.newLocation.x, this.newLocation.y);

                    if (topItem != null && (oldTopItem == null || oldTopItem != topItemNewLocation)) {
                        try {
                            topItem.onWalkOff(this.roomUnit, this.room, new Object[]{this});
                        } catch (Exception e) {
                            LOGGER.error("Caught exception", e);
                        }
                    }

                    if (topItemNewLocation != null && topItemNewLocation != roller && oldTopItem != topItemNewLocation) {
                        try {
                            topItemNewLocation.onWalkOn(this.roomUnit, this.room, new Object[]{this});
                        } catch (Exception e) {
                            LOGGER.error("Caught exception", e);
                        }
                    }
                }
            }, RoomQueueSpeedControlSupport.getEffectiveRollerIntervalMs(this.room));
            /*
            RoomTile rollerTile = room.getLayout().getTile(this.roller.getX(), this.roller.getY());
            Emulator.getThreading().run(() -> {
                if (this.oldLocation == rollerTile && this.roomUnit.getGoal() == rollerTile) {
                    this.roomUnit.setLocation(newLocation);
                    this.roomUnit.setGoalLocation(newLocation);
                    this.roomUnit.setPreviousLocationZ(newLocation.getStackHeight());
                    this.roomUnit.setZ(newLocation.getStackHeight());
                    this.roomUnit.sitUpdate = true;

                    HabboItem topItem = this.room.getTopItemAt(this.roomUnit.getCurrentLocation().x, this.roomUnit.getCurrentLocation().y);
                    if (topItem != null && topItem != roller && oldTopItem != topItem) {
                        try {
                            topItem.onWalkOff(this.roomUnit, this.room, new Object[]{this});
                        } catch (Exception e) {
                            LOGGER.error("Caught exception", e);
                        }
                    }
                }
            }, RoomQueueSpeedControlSupport.getEffectiveRollerIntervalMs(this.room));
             */
        } else {
            this.roomUnit.setLocation(this.newLocation);
            this.roomUnit.setZ(this.newZ);
        }

        return this.response;
    }

    public RoomUnit getRoomUnit() {
        return roomUnit;
    }

    public HabboItem getRoller() {
        return roller;
    }

    public RoomTile getOldLocation() {
        return oldLocation;
    }

    public double getOldZ() {
        return oldZ;
    }

    public RoomTile getNewLocation() {
        return newLocation;
    }

    public double getNewZ() {
        return newZ;
    }

    public Room getRoom() {
        return room;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public HabboItem getOldTopItem() {
        return oldTopItem;
    }
}
