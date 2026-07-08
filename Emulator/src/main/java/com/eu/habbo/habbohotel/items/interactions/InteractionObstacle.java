package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.ICycleable;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.pets.HorsePet;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.rooms.*;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class InteractionObstacle extends HabboItem implements ICycleable {

    private static final int CLEAN_JUMP_STATE = 4;
    private static final int JUMP_DURATION_MS = 2000;

    private static final String HORSE_JUMP_ID_KEY = "obstacleJumpId";
    private static final AtomicInteger JUMP_ID_COUNTER = new AtomicInteger();
    private final AtomicInteger animationSerial = new AtomicInteger();
    private volatile Set<RoomTile> middleTiles;
    private volatile boolean middleTilesCalculated;

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
        return this.allowsUnit(roomUnit, room, false);
    }

    private boolean allowsUnit(RoomUnit roomUnit, Room room, boolean crossingBar) {
        if (roomUnit.getRoomUnitType() == RoomUnitType.PET) {
            Pet pet = room.getPet(roomUnit);

            if (!(pet instanceof HorsePet)) {
                return false;
            }

            return !crossingBar || ((HorsePet) pet).getRider() != null;
        }

        Habbo habbo = room.getHabbo(roomUnit);

        return habbo != null && habbo.getHabboInfo() != null && habbo.getHabboInfo().getRiding() instanceof HorsePet;
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
        Habbo habbo = room.getHabbo(roomUnit);

        if (habbo == null || habbo.getHabboInfo() == null || !(habbo.getHabboInfo().getRiding() instanceof HorsePet)) {
            return;
        }

        RoomTile next = (objects != null && objects.length > 1 && objects[1] instanceof RoomTile)
                ? (RoomTile) objects[1] : null;

        if (next == null) {
            return;
        }

        RoomUnit horseUnit = ((HorsePet) habbo.getHabboInfo().getRiding()).getRoomUnit();

        if (horseUnit == null) {
            return;
        }

        if (this.isMiddleTile(next)) {
            if (!horseUnit.hasStatus(RoomUnitStatus.JUMP)) {
                this.startJump(horseUnit, room, Emulator.getRandom().nextInt(3) + 1);
            }
        } else if (horseUnit.hasStatus(RoomUnitStatus.JUMP)) {
            Object idObj = horseUnit.getCacheable().get(HORSE_JUMP_ID_KEY);

            if (idObj instanceof Integer) {
                int jumpId = (Integer) idObj;

                Emulator.getThreading().run(() -> {
                    Object currentId = horseUnit.getCacheable().get(HORSE_JUMP_ID_KEY);

                    if (currentId instanceof Integer && (Integer) currentId == jumpId) {
                        this.endJump(horseUnit, room);
                    }
                }, 500);
            }
        }
    }

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOn(roomUnit, room, objects);

        Habbo habbo = room.getHabbo(roomUnit);

        if (habbo != null && habbo.getHabboInfo() != null && habbo.getHabboInfo().getRiding() instanceof HorsePet) {
            this.setupRiderJump(habbo, (HorsePet) habbo.getHabboInfo().getRiding(), roomUnit, room);
        }
    }

    private void setupRiderJump(Habbo rider, HorsePet horse, RoomUnit riderUnit, Room room) {
        RoomUnit horseUnit = horse.getRoomUnit();

        if (horseUnit == null) {
            return;
        }

        if (riderUnit.getBodyRotation().getValue() % 2 != 0) {
            return;
        }

        Deque<RoomTile> path = riderUnit.getPath();

        if (path == null || path.isEmpty()) {
            return;
        }

        Deque<RoomTile> jumpPath = new ArrayDeque<>();
        boolean crossesBar = false;

        for (RoomTile tile : path) {
            if (this.isMiddleTile(tile)) {
                crossesBar = true;
                continue;
            }

            jumpPath.add(tile);
        }

        if (!crossesBar || jumpPath.isEmpty()) {
            return;
        }

        riderUnit.setPath(jumpPath);
        Emulator.getThreading().run(() -> this.jumpRider(rider, horse, room), 250);
    }

    private void jumpRider(Habbo rider, HorsePet horse, Room room) {
        if (rider == null || horse == null || room == null
                || this.getRoomId() != room.getId()
                || !rider.isOnline()
                || rider.getHabboInfo() == null
                || rider.getHabboInfo().getCurrentRoom() != room
                || rider.getHabboInfo().getRiding() != horse) {
            return;
        }

        RoomUnit horseUnit = horse.getRoomUnit();

        if (horseUnit == null) {
            return;
        }

        this.startJump(horseUnit, room, CLEAN_JUMP_STATE);

        AchievementManager.progressAchievement(rider, Emulator.getGameEnvironment().getAchievementManager().getAchievement("HorseConsecutiveJumpsCount"));
        AchievementManager.progressAchievement(rider, Emulator.getGameEnvironment().getAchievementManager().getAchievement("HorseJumping"));
    }

    private void startJump(RoomUnit horseUnit, Room room, int state) {
        int jumpId = JUMP_ID_COUNTER.incrementAndGet();
        horseUnit.getCacheable().put(HORSE_JUMP_ID_KEY, jumpId);
        horseUnit.setStatus(RoomUnitStatus.JUMP, "0");
        this.showAnimation(room, state);

        Emulator.getThreading().run(() -> {
            Object currentId = horseUnit.getCacheable().get(HORSE_JUMP_ID_KEY);

            if (currentId instanceof Integer && (Integer) currentId == jumpId) {
                this.endJump(horseUnit, room);
            }
        }, JUMP_DURATION_MS);
    }

    private void showAnimation(Room room, int state) {
        int serial = this.animationSerial.incrementAndGet();

        if (!"0".equals(this.getExtradata())) {
            this.setExtradata("0");
            room.updateItemState(this);
        }

        this.setExtradata(Integer.toString(state));
        room.updateItemState(this);

        Emulator.getThreading().run(() -> {
            if (this.animationSerial.get() == serial && this.getRoomId() == room.getId()) {
                this.setExtradata("0");
                room.updateItemState(this);
            }
        }, JUMP_DURATION_MS);
    }

    private void endJump(RoomUnit horseUnit, Room room) {
        horseUnit.getCacheable().remove(HORSE_JUMP_ID_KEY);

        if (horseUnit.hasStatus(RoomUnitStatus.JUMP)) {
            horseUnit.removeStatus(RoomUnitStatus.JUMP);
            horseUnit.statusUpdate(true);
            room.sendComposer(new RoomUserStatusComposer(horseUnit).compose());
        }
    }

    @Override
    public void onWalkOff(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOff(roomUnit, room, objects);

        Habbo habbo = room.getHabbo(roomUnit);

        if (habbo == null || habbo.getHabboInfo() == null || !(habbo.getHabboInfo().getRiding() instanceof HorsePet)) {
            return;
        }

        RoomTile next = (objects != null && objects.length > 1 && objects[1] instanceof RoomTile)
                ? (RoomTile) objects[1] : null;

        boolean stillOnObstacle = false;
        if (next != null) {
            for (HabboItem item : room.getItemsAt(next)) {
                if (item == this) {
                    stillOnObstacle = true;
                    break;
                }
            }
        }

        if (!stillOnObstacle) {
            RoomUnit horseUnit = ((HorsePet) habbo.getHabboInfo().getRiding()).getRoomUnit();

            if (horseUnit != null) {
                this.endJump(horseUnit, room);
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
        this.middleTiles = new HashSet<>();
        this.middleTilesCalculated = false;
    }

    @Override
    public void onMove(Room room, RoomTile oldLocation, RoomTile newLocation) {
        super.onMove(room, oldLocation, newLocation);
        this.calculateMiddleTiles(room);
    }

    private void calculateMiddleTiles(Room room) {
        Set<RoomTile> tiles = new HashSet<>();

        if(this.getRotation() == 2) {
            tiles.add(room.getLayout().getTile((short)(this.getX() + 1), this.getY()));
            tiles.add(room.getLayout().getTile((short)(this.getX() + 1), (short)(this.getY() + 1)));
        }
        else if(this.getRotation() == 4) {
            tiles.add(room.getLayout().getTile(this.getX(), (short)(this.getY() + 1)));
            tiles.add(room.getLayout().getTile((short)(this.getX() + 1), (short)(this.getY() + 1)));
        }

        tiles.remove(null);
        this.middleTiles = tiles;
        this.middleTilesCalculated = true;
    }

    @Override
    public RoomTile getOverrideGoalTile(RoomUnit roomUnit, Room room, RoomTile tile) {
        if(this.isMiddleTile(tile))
            return null;

        return tile;
    }

    @Override
    public RoomTileState getOverrideTileState(RoomTile tile, Room room) {
        return RoomTileState.BLOCKED;
    }

    @Override
    public boolean canOverrideTile(RoomUnit roomUnit, Room room, RoomTile tile) {
        return this.allowsUnit(roomUnit, room, this.isMiddleTile(tile));
    }

    private boolean isMiddleTile(RoomTile tile) {
        if(tile == null)
            return false;

        for(RoomTile middle : this.middleTiles) {
            if(middle != null && middle.x == tile.x && middle.y == tile.y)
                return true;
        }

        return false;
    }

    @Override
    public void cycle(Room room) {
        if(!this.middleTilesCalculated) {
            this.calculateMiddleTiles(room);
        }

        if(this.middleTiles.isEmpty()) {
            return;
        }

        for(RoomTile tile : this.middleTiles) {
            if(tile == null || !tile.hasUnits()) {
                continue;
            }

            for(RoomUnit unit : tile.getUnits()) {
                if(unit.getPath().size() == 0 && !unit.hasStatus(RoomUnitStatus.MOVE)) {
                    RoomUserRotation opposite = unit.getBodyRotation().getOpposite();

                    if(unit.getBodyRotation().getValue() != this.getRotation() && (opposite == null || opposite.getValue() != this.getRotation()))
                        continue;

                    RoomTile pushTo = null;

                    RoomTile tileInfront = room.getLayout().getTileInFront(unit.getCurrentLocation(), unit.getBodyRotation().getValue());
                    if(tileInfront != null && tileInfront.state != RoomTileState.INVALID && tileInfront.state != RoomTileState.BLOCKED && room.getRoomUnitsAt(tileInfront).size() == 0) {
                        pushTo = tileInfront;
                    }
                    else if(opposite != null) {
                        RoomTile tileBehind = room.getLayout().getTileInFront(unit.getCurrentLocation(), opposite.getValue());
                        if(tileBehind != null && tileBehind.state != RoomTileState.INVALID && tileBehind.state != RoomTileState.BLOCKED && room.getRoomUnitsAt(tileBehind).size() == 0) {
                            pushTo = tileBehind;
                        }
                    }

                    if(pushTo != null) {
                        unit.setGoalLocation(pushTo);

                        if("0".equals(this.getExtradata())) {
                            this.showAnimation(room, Emulator.getRandom().nextInt(3) + 1);
                        }
                    }
                }
            }
        }
    }
}
