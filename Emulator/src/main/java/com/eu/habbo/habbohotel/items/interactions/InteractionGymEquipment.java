package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.items.ICycleable;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitType;
import com.eu.habbo.habbohotel.rooms.RoomUserRotation;
import com.eu.habbo.habbohotel.users.Habbo;
import java.sql.ResultSet;
import java.sql.SQLException;

public class InteractionGymEquipment extends InteractionEffectTile implements ICycleable {
    private int startTime = 0;
    private int roomUnitId = -1;

    public InteractionGymEquipment(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionGymEquipment(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return this.roomUnitId == -1
                && super.canWalkOn(roomUnit, room, objects)
                && (roomUnit.getRoomUnitType().equals(RoomUnitType.USER)
                        || roomUnit.getRoomUnitType().equals(RoomUnitType.BOT));
    }

    @Override
    public boolean isWalkable() {
        return this.roomUnitId == -1;
    }

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOn(roomUnit, room, objects);

        if (this.forceRotation()) {
            roomUnit.setRotation(RoomUserRotation.fromValue(this.getRotation()));
            roomUnit.canRotate = false;
        }
        this.roomUnitId = roomUnit.getId();

        if (roomUnit.getRoomUnitType() == RoomUnitType.USER) {
            Habbo habbo = room.getHabbo(roomUnit);

            if (habbo != null) {
                this.startTime = Emulator.getIntUnixTimestamp();
            }
        }
    }

    @Override
    public void onWalkOff(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOff(roomUnit, room, objects);
        room.giveEffect(roomUnit, 0, -1);

        if (this.forceRotation()) {
            roomUnit.canRotate = true;
        }

        this.reset(room);
    }

    public String achievementName() {
        return Emulator.getConfig()
                .getValue("hotel.furni.gym.achievement." + this.getBaseItem().getName(), "");
    }

    public boolean forceRotation() {
        return Emulator.getConfig()
                .getBoolean("hotel.furni.gym.forcerot." + this.getBaseItem().getName(), true);
    }

    @Override
    public void cycle(Room room) {
        if (this.roomUnitId != -1) {
            Habbo habbo = room.getHabboByRoomUnitId(this.roomUnitId);

            if (habbo != null) {
                int timestamp = Emulator.getIntUnixTimestamp();
                if (timestamp - this.startTime >= 120) {
                    String achievement = this.achievementName();

                    if (!achievement.isEmpty()) {
                        AchievementManager.progressAchievement(
                                habbo.getHabboInfo().getId(),
                                Emulator.getGameEnvironment()
                                        .getAchievementManager()
                                        .getAchievement(achievement));
                    }

                    this.startTime = timestamp;
                }
            }
        }
    }

    @Override
    public void setRotation(int rotation) {
        super.setRotation(rotation);

        if (this.forceRotation() && this.roomUnitId != -1) {
            Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
            if (room != null) {
                RoomUnit roomUnit = this.getCurrentRoomUnit(room);

                if (roomUnit != null) {
                    roomUnit.setRotation(RoomUserRotation.fromValue(rotation));
                    room.updateRoomUnit(roomUnit);
                }
            }
        }
    }

    @Override
    public void onPickUp(Room room) {
        super.onPickUp(room);

        if (this.roomUnitId != -1) {
            this.setEffect(room, 0);
        }

        this.reset(room);
    }

    @Override
    public void onMove(Room room, RoomTile oldLocation, RoomTile newLocation) {
        super.onMove(room, oldLocation, newLocation);
        if (!oldLocation.equals(newLocation)) {
            this.setEffect(room, 0);
            this.reset(room);
        }
    }

    private void setEffect(Room room, int effectId) {
        if (this.roomUnitId == -1) return;

        room.giveEffect(this.getCurrentRoomUnit(room), effectId, -1);
    }

    private void reset(Room room) {
        this.roomUnitId = -1;
        this.startTime = 0;
        this.setExtradata("0");
        room.updateItem(this);
    }

    private RoomUnit getCurrentRoomUnit(Room room) {
        Habbo habbo = room.getHabboByRoomUnitId(this.roomUnitId);
        if (habbo != null) {
            return habbo.getRoomUnit();
        } else {
            Bot bot = room.getBotByRoomUnitId(this.roomUnitId);
            if (bot != null) {
                return bot.getRoomUnit();
            }
        }

        return null;
    }
}
