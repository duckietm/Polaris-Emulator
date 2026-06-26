package com.eu.habbo.habbohotel.items.interactions.games.football;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.games.GameTeamColors;
import com.eu.habbo.habbohotel.games.football.FootballGame;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionPushable;
import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameTeamItem;
import com.eu.habbo.habbohotel.items.interactions.games.football.goals.InteractionFootballGoal;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.pets.PetTasks;
import com.eu.habbo.habbohotel.rooms.*;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.rooms.items.ItemStateComposer;
import com.eu.habbo.util.pathfinding.Rotation;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;


public class InteractionFootball extends InteractionPushable {

    public static final int MAX_FOOTBALL_PETS = 5;

    public InteractionFootball(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionFootball(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }


    @Override
    public int getWalkOnVelocity(RoomUnit roomUnit, Room room) {
        if (roomUnit.getPath().isEmpty() && roomUnit.tilesWalked() == 2 && this.getExtradata().equals("1"))
            return 0;

        if (roomUnit.getPath().size() == 0 && roomUnit.tilesWalked() == 1)
            return 6;

        return 1;
    }

    @Override
    public int getWalkOffVelocity(RoomUnit roomUnit, Room room) {
        if (roomUnit.getPath().size() == 0 && roomUnit.tilesWalked() == 0)
            return 6;

        return 1;
    }

    @Override
    public int getDragVelocity(RoomUnit roomUnit, Room room) {
        if (roomUnit.getPath().isEmpty() && roomUnit.tilesWalked() == 2)
            return 0;

        return 1;
    }

    @Override
    public int getTackleVelocity(RoomUnit roomUnit, Room room) {
        return 4;
    }


    @Override
    public RoomUserRotation getWalkOnDirection(RoomUnit roomUnit, Room room) {
        return roomUnit.getBodyRotation();
    }

    @Override
    public RoomUserRotation getWalkOffDirection(RoomUnit roomUnit, Room room) {
        RoomTile peek = roomUnit.getPath().peek();
        RoomTile nextWalkTile = peek != null ? room.getLayout().getTile(peek.x, peek.y) : roomUnit.getGoal();
        return RoomUserRotation.values()[(RoomUserRotation.values().length + Rotation.Calculate(roomUnit.getX(), roomUnit.getY(), nextWalkTile.x, nextWalkTile.y) + 4) % 8];
    }

    public RoomUserRotation getDragDirection(RoomUnit roomUnit, Room room) {
        return roomUnit.getBodyRotation();
    }

    public RoomUserRotation getTackleDirection(RoomUnit roomUnit, Room room) {
        return roomUnit.getBodyRotation();
    }


    @Override
    public int getNextRollDelay(int currentStep, int totalSteps) {

        if(totalSteps > 4) {
            if(currentStep <= 4) {
                return 125;
            }
        }

        return 500;

        /*int t = 2500;
        return (totalSteps == 1) ? 500 : 100 * ((t = t / t - 1) * t * t * t * t + 1) + (currentStep * 100);*/
    }

    @Override
    public RoomUserRotation getBounceDirection(Room room, RoomUserRotation currentDirection) {
        switch (currentDirection) {
            case NORTH -> {
                return RoomUserRotation.SOUTH;
            }
            case NORTH_EAST -> {
                if (this.validMove(room, room.getLayout().getTile(this.getX(), this.getY()), room.getLayout().getTileInFront(room.getLayout().getTile(this.getX(), this.getY()), RoomUserRotation.NORTH_WEST.getValue())))
                    return RoomUserRotation.NORTH_WEST;
                else if (this.validMove(room, room.getLayout().getTile(this.getX(), this.getY()), room.getLayout().getTileInFront(room.getLayout().getTile(this.getX(), this.getY()), RoomUserRotation.SOUTH_EAST.getValue())))
                    return RoomUserRotation.SOUTH_EAST;
                else
                    return RoomUserRotation.SOUTH_WEST;
            }
            case EAST -> {
                return RoomUserRotation.WEST;
            }
            case SOUTH_EAST -> {
                if (this.validMove(room, room.getLayout().getTile(this.getX(), this.getY()), room.getLayout().getTileInFront(room.getLayout().getTile(this.getX(), this.getY()), RoomUserRotation.SOUTH_WEST.getValue())))
                    return RoomUserRotation.SOUTH_WEST;
                else if (this.validMove(room, room.getLayout().getTile(this.getX(), this.getY()), room.getLayout().getTileInFront(room.getLayout().getTile(this.getX(), this.getY()), RoomUserRotation.NORTH_EAST.getValue())))
                    return RoomUserRotation.NORTH_EAST;
                else
                    return RoomUserRotation.NORTH_WEST;
            }
            case SOUTH -> {
                return RoomUserRotation.NORTH;
            }
            case SOUTH_WEST -> {
                if (this.validMove(room, room.getLayout().getTile(this.getX(), this.getY()), room.getLayout().getTileInFront(room.getLayout().getTile(this.getX(), this.getY()), RoomUserRotation.SOUTH_EAST.getValue())))
                    return RoomUserRotation.SOUTH_EAST;
                else if (this.validMove(room, room.getLayout().getTile(this.getX(), this.getY()), room.getLayout().getTileInFront(room.getLayout().getTile(this.getX(), this.getY()), RoomUserRotation.NORTH_WEST.getValue())))
                    return RoomUserRotation.NORTH_WEST;
                else
                    return RoomUserRotation.NORTH_EAST;
            }
            case WEST -> {
                return RoomUserRotation.EAST;
            }
            case NORTH_WEST -> {
                if (this.validMove(room, room.getLayout().getTile(this.getX(), this.getY()), room.getLayout().getTileInFront(room.getLayout().getTile(this.getX(), this.getY()), RoomUserRotation.NORTH_EAST.getValue())))
                    return RoomUserRotation.NORTH_EAST;
                else if (this.validMove(room, room.getLayout().getTile(this.getX(), this.getY()), room.getLayout().getTileInFront(room.getLayout().getTile(this.getX(), this.getY()), RoomUserRotation.SOUTH_WEST.getValue())))
                    return RoomUserRotation.SOUTH_WEST;
                else
                    return RoomUserRotation.SOUTH_EAST;
            }
            default -> {
                return RoomUserRotation.SOUTH;
            }
        }
    }


    @Override
    public boolean validMove(Room room, RoomTile from, RoomTile to) {
        if (to == null || to.state == RoomTileState.INVALID) return false;
        HabboItem topItem = room.getTopItemAt(to.x, to.y, this);

        // Move is valid if there isnt any furni yet
        if (topItem == null) {
            return true;
        }

        // If any furni on tile is not stackable, move is invalid (tested on 22-03-2022)
        if (room.getItemsAt(to).stream().anyMatch(x -> !x.getBaseItem().allowStack())) {
            return false;
        }

        // Ball can only go up by 1.65 according to Habbo (tested using stack tile on 22-03-2022)
        BigDecimal topItemHeight = BigDecimal.valueOf(topItem.getZ() + topItem.getBaseItem().getHeight());
        BigDecimal ballHeight = BigDecimal.valueOf(this.getZ());

        if (topItemHeight.subtract(ballHeight).compareTo(new BigDecimal(1.65)) > 0) {
            return false;
        }

        // If top item is a football goal, the move is only valid if ball is coming from the front side
        // Ball shouldn't come from the back or from the sides (tested on 22-03-2022)
        if (topItem instanceof InteractionFootballGoal) {
            int ballDirection = Rotation.Calculate(from.x, from.y, to.x, to.y);
            int goalRotation = topItem.getRotation();

            switch (goalRotation) {
                case 0 -> {
                    return ballDirection > 2 && ballDirection < 6;
                }
                case 2 -> {
                    return ballDirection > 4;
                }
                case 4 -> {
                    return ballDirection > 6 || ballDirection < 2;
                }
                case 6 -> {
                    return ballDirection > 0 && ballDirection < 4;
                }
            }
        }

        return topItem.getBaseItem().allowStack();
    }

    //Events

    private void triggerPetFollowBall(Room room, RoomTile ballTile) {
        if (ballTile == null) return;

        Collection<Pet> pets = room.getUnitManager().getPets();
        if (pets.isEmpty()) return;

        for (Pet pet : pets) {
            if (pet.getRoomUnit() == null) continue;
            if (pet.getTask() == PetTasks.PLAY_FOOTBALL) {
                pet.getRoomUnit().setGoalLocation(ballTile);
            }
        }
    }

    public void petKick(Room room, RoomUnit petUnit) {
        if (isMoving()) return;
        int velocity = this.getWalkOnVelocity(petUnit, room);
        RoomUserRotation direction = this.getWalkOnDirection(petUnit, room);
        this.onKick(room, petUnit, velocity, direction);
        this.initiateKick(room, petUnit, velocity, direction);
    }

    @Override
    public void onDrag(Room room, RoomUnit roomUnit, int velocity, RoomUserRotation direction) {

    }

    @Override
    public void onKick(Room room, RoomUnit roomUnit, int velocity, RoomUserRotation direction) {
        triggerPetFollowBall(room, room.getLayout().getTile(this.getX(), this.getY()));
    }

    @Override
    public void onTackle(Room room, RoomUnit roomUnit, int velocity, RoomUserRotation direction) {

    }

    @Override
    public void onMove(Room room, RoomTile from, RoomTile to, RoomUserRotation direction, RoomUnit kicker, int nextRoll, int currentStep, int totalSteps) {
        var game = (FootballGame) room.getGame(FootballGame.class);
        if (game == null) {
            try {
                game = FootballGame.class.getDeclaredConstructor(new Class[]{Room.class}).newInstance(room);
                room.addGame(game);
            } catch (Exception e) {
                return;
            }
        }
        HabboItem currentTopItem = room.getTopItemAt(from.x, from.y, this);
        HabboItem topItem = room.getTopItemAt(to.x, to.y, this);
        if ((topItem != null) && ((currentTopItem == null) || (currentTopItem.getId() != topItem.getId())) && ((topItem instanceof InteractionFootballGoal))) {
            GameTeamColors color = ((InteractionGameTeamItem) topItem).teamColor;
            game.onScore(kicker, color);
        }

        this.setExtradata(Math.abs(currentStep - (totalSteps + 1)) + "");
        room.sendComposer(new ItemStateComposer(this).compose());

        /*this.setExtradata(nextRoll <= 200 ? "8" : (nextRoll <= 250 ? "7" : (nextRoll <= 300 ? "6" : (nextRoll <= 350 ? "5" : (nextRoll <= 400 ? "4" : (nextRoll <= 450 ? "3" : (nextRoll <= 500 ? "2" : "1")))))));
        room.sendComposer(new ItemStateComposer(this).compose());*/
    }

    @Override
    public void onBounce(Room room, RoomUserRotation oldDirection, RoomUserRotation newDirection, RoomUnit kicker) {

    }

    @Override
    public void onStop(Room room, RoomUnit kicker, int currentStep, int totalSteps) {
        this.setExtradata("0");
        room.sendComposer(new ItemStateComposer(this).compose());

        triggerPetFollowBall(room, room.getLayout().getTile(this.getX(), this.getY()));
    }

    @Override
    public boolean canStillMove(Room room, RoomTile from, RoomTile to, RoomUserRotation direction, RoomUnit kicker, int nextRoll, int currentStep, int totalSteps) {
        HabboItem topItem = room.getTopItemAt(from.x, from.y, this);
        return !((Emulator.getRandom().nextInt(10) >= 3 && room.hasHabbosAt(to.x, to.y)) || (topItem != null && topItem.getBaseItem().getName().startsWith("fball_goal_") && currentStep != 1));
    }

    @Override
    public void onPickUp(Room room) {
        this.setExtradata("0");
    }

}