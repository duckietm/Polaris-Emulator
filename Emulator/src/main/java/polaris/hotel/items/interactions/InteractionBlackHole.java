package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.Emulator;
import polaris.hotel.achievements.Achievement;
import polaris.hotel.achievements.AchievementManager;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;

public class InteractionBlackHole extends InteractionGate {
    public InteractionBlackHole(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionBlackHole(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void onPlace(Room room) {
        Achievement holeCountAchievement =
                Emulator.getGameEnvironment().getAchievementManager().getAchievement("RoomDecoHoleFurniCount");

        int holesCountProgress = 0;
        User owner = room.getUser(this.getUserId());

        if (owner == null) {
            holesCountProgress =
                    AchievementManager.getAchievementProgressForUser(this.getUserId(), holeCountAchievement);
        } else {
            holesCountProgress = owner.getUserStats().getAchievementProgress(holeCountAchievement);
        }
        int holeDifference = room.getRoomSpecialTypes()
                        .getItemsOfType(InteractionBlackHole.class)
                        .size()
                - holesCountProgress;

        if (holeDifference > 0) {
            if (owner != null) {
                AchievementManager.progressAchievement(owner, holeCountAchievement, holeDifference);
            } else {
                AchievementManager.progressAchievement(this.getUserId(), holeCountAchievement, holeDifference);
            }
        }

        super.onPlace(room);
    }
}
