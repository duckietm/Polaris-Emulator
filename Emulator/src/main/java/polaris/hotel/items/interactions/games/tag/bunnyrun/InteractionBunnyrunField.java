package polaris.hotel.items.interactions.games.tag.bunnyrun;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.Emulator;
import polaris.hotel.achievements.AchievementManager;
import polaris.hotel.games.tag.BunnyrunGame;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.games.tag.InteractionTagField;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;

public class InteractionBunnyrunField extends InteractionTagField {
    public InteractionBunnyrunField(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem, BunnyrunGame.class);
    }

    public InteractionBunnyrunField(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells, BunnyrunGame.class);
    }

    @Override
    public void onPlace(Room room) {
        super.onPlace(room);

        User itemOwner = Emulator.getGameEnvironment().getUserManager().getUser(this.getUserId());

        if (itemOwner != null) {
            AchievementManager.progressAchievement(
                    itemOwner,
                    Emulator.getGameEnvironment().getAchievementManager().getAchievement("RbBunnyTag"));
        }
    }
}
