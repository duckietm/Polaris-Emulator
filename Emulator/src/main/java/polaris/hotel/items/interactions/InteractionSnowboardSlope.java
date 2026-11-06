package polaris.hotel.items.interactions;

import java.awt.Rectangle;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import polaris.Emulator;
import polaris.hotel.achievements.Achievement;
import polaris.hotel.achievements.AchievementManager;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomLayout;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;

public class InteractionSnowboardSlope extends InteractionMultiHeight {
    public InteractionSnowboardSlope(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    public InteractionSnowboardSlope(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        room.giveEffect(roomUnit, 97, -1);
    }

    @Override
    public void onWalkOff(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOff(roomUnit, room, objects);

        if (roomUnit.getEffectId() == 97) {
            room.giveEffect(roomUnit, 0, -1);
        }
    }

    @Override
    public void onPlace(Room room) {
        super.onPlace(room);
        Set<UserItem> items = room.getRoomSpecialTypes().getItemsOfType(InteractionSnowboardSlope.class);

        Achievement snowboardBuild =
                Emulator.getGameEnvironment().getAchievementManager().getAchievement("snowBoardBuild");

        if (snowboardBuild == null) return;
        int progress;
        User user = room.getUser(room.getOwnerId());

        if (user != null) {
            progress = user.getUserStats().getAchievementProgress(snowboardBuild);

        } else {
            progress = AchievementManager.getAchievementProgressForUser(room.getOwnerId(), snowboardBuild);
        }

        progress = Math.max(items.size() - progress, 0);

        if (progress > 0) {
            AchievementManager.progressAchievement(room.getOwnerId(), snowboardBuild);
        }
    }

    @Override
    public void onPickUp(Room room) {
        for (User user : room.getUsersOnItem(this)) {
            if (user.getRoomUnit().getEffectId() == 97) {
                room.giveEffect(user, 0, -1);
            }
        }
    }

    @Override
    public void onMove(Room room, RoomTile oldLocation, RoomTile newLocation) {
        Rectangle newRect = RoomLayout.getRectangle(
                newLocation.x,
                newLocation.y,
                this.getBaseItem().getWidth(),
                this.getBaseItem().getLength(),
                this.getRotation());

        for (User user : room.getUsersOnItem(this)) {
            if (user.getRoomUnit().getEffectId() == 97
                    && !newRect.contains(
                            user.getRoomUnit().getCurrentLocation().x,
                            user.getRoomUnit().getCurrentLocation().y)) {
                room.giveEffect(user, 0, -1);
            }
        }
    }
}
