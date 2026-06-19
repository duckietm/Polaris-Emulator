package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.ICycleable;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.pets.HorsePet;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.pets.PetTasks;
import com.eu.habbo.habbohotel.rooms.*;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.threading.runnables.HabboItemNewState;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class InteractionObstacle extends HabboItem implements ICycleable {

    private Set<RoomTile> middleTiles;

    public InteractionObstacle(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.setExtradata("0");
        this.middleTiles = new HashSet<>();
    }

    public InteractionObstacle(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.setExtradata("0");
        this.middleTiles = new HashSet<>();
    }

    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        serverMessage.appendInt((this.isLimited() ? 256 : 0));
        serverMessage.appendString(this.getExtradata());

        super.serializeExtradata(serverMessage);
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return true;
    }

    @Override
    public boolean isWalkable() {
        return true;
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        super.onClick(client, room, objects);
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        Pet pet = room.getPet(roomUnit);

        if (pet instanceof HorsePet && ((HorsePet) pet).getRider() != null) {
            Habbo rider = ((HorsePet) pet).getRider();
            if (pet.getTask() != null && pet.getTask().equals(PetTasks.RIDE)) {
                if (pet.getRoomUnit().hasStatus(RoomUnitStatus.JUMP)) {
                    pet.getRoomUnit().removeStatus(RoomUnitStatus.JUMP);
                    Emulator.getThreading().run(new HabboItemNewState(this, room, "0"), 2000);
                } else {
                    int state = 0;
                    for (int i = 0; i < 2; i++) {
                        state = Emulator.getRandom().nextInt(4) + 1;

                        if (state == 4)
                            break;
                    }

                    this.setExtradata(state + "");
                    pet.getRoomUnit().setStatus(RoomUnitStatus.JUMP, "0");

                    AchievementManager.progressAchievement(rider, Emulator.getGameEnvironment().getAchievementManager().getAchievement("HorseConsecutiveJumpsCount"));
                    AchievementManager.progressAchievement(rider, Emulator.getGameEnvironment().getAchievementManager().getAchievement("HorseJumping"));
                }

                room.updateItemState(this);
            }
        }
    }

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOn(roomUnit, room, objects);

        Habbo habbo = room.getHabbo(roomUnit);

        if (habbo == null) {
            Pet pet = room.getPet(roomUnit);

            if (pet instanceof HorsePet && ((HorsePet) pet).getRider() != null) {
                if (roomUnit.getBodyRotation().getValue() % 2 == 0) {
                    if (this.getRotation() == 2) {
                        if (roomUnit.getBodyRotation().equals(RoomUserRotation.WEST)) {
                            ((HorsePet) pet).getRider().getRoomUnit().setGoalLocation(room.getLayout().getTile((short) (roomUnit.getX() - 3), roomUnit.getY()));
                        } else if (roomUnit.getBodyRotation().equals(RoomUserRotation.EAST)) {
                            ((HorsePet) pet).getRider().getRoomUnit().setGoalLocation(room.getLayout().getTile((short) (roomUnit.getX() + 3), roomUnit.getY()));
                        }
                    } else if (this.getRotation() == 4) {
                        if (roomUnit.getBodyRotation().equals(RoomUserRotation.NORTH)) {
                            ((HorsePet) pet).getRider().getRoomUnit().setGoalLocation(room.getLayout().getTile(roomUnit.getX(), (short) (roomUnit.getY() - 3)));
                        } else if (roomUnit.getBodyRotation().equals(RoomUserRotation.SOUTH)) {
                            ((HorsePet) pet).getRider().getRoomUnit().setGoalLocation(room.getLayout().getTile(roomUnit.getX(), (short) (roomUnit.getY() + 3)));
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onWalkOff(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOff(roomUnit, room, objects);

        Habbo habbo = room.getHabbo(roomUnit);

        if (habbo == null) {
            Pet pet = room.getPet(roomUnit);

            if (pet instanceof HorsePet && ((HorsePet) pet).getRider() != null) {
                pet.getRoomUnit().removeStatus(RoomUnitStatus.JUMP);
            }
        }
    }

    @Override
    public void onPlace(Room room) {
        super.onPlace(room);
        this.calculateMiddleTiles(room);
    }

    @Override
    public void onPickUp(Room room) {
        super.onPickUp(room);
        middleTiles.clear();
    }

    @Override
    public void onMove(Room room, RoomTile oldLocation, RoomTile newLocation) {
        super.onMove(room, oldLocation, newLocation);
        this.calculateMiddleTiles(room);
    }

    private void calculateMiddleTiles(Room room) {
        middleTiles.clear();

        if(this.getRotation() == 2) {
            middleTiles.add(room.getLayout().getTile((short)(this.getX() + 1), this.getY()));
            middleTiles.add(room.getLayout().getTile((short)(this.getX() + 1), (short)(this.getY() + 1)));
        }
        else if(this.getRotation() == 4) {
            middleTiles.add(room.getLayout().getTile(this.getX(), (short)(this.getY() + 1)));
            middleTiles.add(room.getLayout().getTile((short)(this.getX() + 1), (short)(this.getY() + 1)));
        }
    }

    @Override
    public RoomTileState getOverrideTileState(RoomTile tile, Room room) {
        if(this.middleTiles.contains(tile))
            return RoomTileState.BLOCKED;

        return null;
    }

    @Override
    public void cycle(Room room) {
        if(this.middleTiles.size() == 0) {
            this.calculateMiddleTiles(room);
        }

        for(RoomTile tile : this.middleTiles) {
            for(RoomUnit unit : tile.getUnits()) {
                if(unit.getPath().size() == 0 && !unit.hasStatus(RoomUnitStatus.MOVE)) {
                    if(unit.getBodyRotation().getValue() != this.getRotation() && Objects.requireNonNull(unit.getBodyRotation().getOpposite()).getValue() != this.getRotation())
                        continue;

                    RoomTile tileInfront = room.getLayout().getTileInFront(unit.getCurrentLocation(), unit.getBodyRotation().getValue());
                    if(tileInfront.state != RoomTileState.INVALID && tileInfront.state != RoomTileState.BLOCKED && room.getRoomUnitsAt(tileInfront).size() == 0) {
                        unit.setGoalLocation(tileInfront);
                    }
                    else {
                        RoomTile tileBehind = room.getLayout().getTileInFront(unit.getCurrentLocation(), Objects.requireNonNull(unit.getBodyRotation().getOpposite()).getValue());
                        if(tileBehind.state != RoomTileState.INVALID && tileBehind.state != RoomTileState.BLOCKED && room.getRoomUnitsAt(tileBehind).size() == 0) {
                            unit.setGoalLocation(tileBehind);
                        }
                    }
                }
            }
        }
    }
}
