package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.Emulator;
import polaris.hotel.achievements.AchievementManager;
import polaris.hotel.bots.Bot;
import polaris.hotel.items.ICycleable;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.rooms.RoomUnitType;
import polaris.hotel.rooms.RoomUserRotation;
import polaris.hotel.users.User;

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
            User user = room.getUser(roomUnit);

            if (user != null) {
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
            User user = room.getUserByRoomUnitId(this.roomUnitId);

            if (user != null) {
                int timestamp = Emulator.getIntUnixTimestamp();
                if (timestamp - this.startTime >= 120) {
                    String achievement = this.achievementName();

                    if (!achievement.isEmpty()) {
                        AchievementManager.progressAchievement(
                                user.getUserInfo().getId(),
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
        User user = room.getUserByRoomUnitId(this.roomUnitId);
        if (user != null) {
            return user.getRoomUnit();
        } else {
            Bot bot = room.getBotByRoomUnitId(this.roomUnitId);
            if (bot != null) {
                return bot.getRoomUnit();
            }
        }

        return null;
    }
}
