package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.Emulator;
import polaris.hotel.achievements.AchievementManager;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;

public class InteractionVikingCotie extends InteractionDefault {
    public InteractionVikingCotie(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionVikingCotie(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (this.getExtradata().isEmpty()) {
            this.setExtradata("0");
        }

        if (client != null && client.getUser().getUserInfo().getId() == this.getUserId()) {
            if (client.getUser().getRoomUnit().getEffectId() == 172
                    || client.getUser().getRoomUnit().getEffectId() == 173) {
                int state = Integer.parseInt(this.getExtradata());

                if (state < 5) {
                    state++;
                    this.setExtradata(state + "");
                    room.updateItem(this);

                    if (state == 5) {
                        AchievementManager.progressAchievement(
                                client.getUser(),
                                Emulator.getGameEnvironment()
                                        .getAchievementManager()
                                        .getAchievement("ViciousViking"));
                    }
                }
            }
        }
    }

    @Override
    public boolean allowWiredResetState() {
        return false;
    }
}
