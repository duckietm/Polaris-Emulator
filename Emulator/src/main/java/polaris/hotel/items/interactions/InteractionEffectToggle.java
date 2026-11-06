package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserGender;

public class InteractionEffectToggle extends InteractionDefault {
    public InteractionEffectToggle(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionEffectToggle(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (this.getExtradata().isEmpty()) {
            this.setExtradata("0");
        }

        if (client != null) {
            if (room.hasRights(client.getUser())) {
                if (Integer.parseInt(this.getExtradata()) < this.getBaseItem().getStateCount() - 1) {
                    if ((client.getUser().getUserInfo().getGender() == UserGender.M
                                    && client.getUser().getRoomUnit().getEffectId()
                                            == this.getBaseItem().getEffectM())
                            || (client.getUser().getUserInfo().getGender() == UserGender.F
                                    && client.getUser().getRoomUnit().getEffectId()
                                            == this.getBaseItem().getEffectF())) {
                        super.onClick(client, room, objects);
                    }
                }
            }
        }
    }
}
