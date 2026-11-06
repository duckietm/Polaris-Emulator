package polaris.hotel.items.interactions.games.tag.rollerskate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import polaris.Emulator;
import polaris.hotel.achievements.AchievementManager;
import polaris.hotel.games.tag.RollerskateGame;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.games.tag.InteractionTagField;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;

public class InteractionRollerskateField extends InteractionTagField {
    private final HashMap<User, Integer> stepTimes = new HashMap<>();

    public InteractionRollerskateField(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem, RollerskateGame.class);
    }

    public InteractionRollerskateField(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells, RollerskateGame.class);
    }

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOn(roomUnit, room, objects);

        User user = room.getUser(roomUnit);
        if (user != null) this.stepTimes.put(user, Emulator.getIntUnixTimestamp());
    }

    @Override
    public void onWalkOff(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOff(roomUnit, room, objects);

        User user = room.getUser(roomUnit);
        if (user != null && this.stepTimes.containsKey(user)) {
            AchievementManager.progressAchievement(
                    user,
                    Emulator.getGameEnvironment().getAchievementManager().getAchievement("RbTagC"),
                    (Emulator.getIntUnixTimestamp() - this.stepTimes.get(user)) / 60);
            this.stepTimes.remove(user);
        }
    }

    @Override
    public void onPlace(Room room) {
        super.onPlace(room);

        User itemOwner = Emulator.getGameEnvironment().getUserManager().getUser(this.getUserId());

        if (itemOwner != null) {
            AchievementManager.progressAchievement(
                    itemOwner,
                    Emulator.getGameEnvironment().getAchievementManager().getAchievement("RbTagA"));
        }
    }
}
