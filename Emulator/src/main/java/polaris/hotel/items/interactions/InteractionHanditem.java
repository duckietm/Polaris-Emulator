package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomLayout;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.UserItem;

public class InteractionHanditem extends InteractionDefault {
    public InteractionHanditem(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionHanditem(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        super.onClick(client, room, objects);

        if (RoomLayout.tilesAdjecent(
                        client.getUser().getRoomUnit().getCurrentLocation(),
                        room.getLayout().getTile(this.getX(), this.getY()))
                || (client.getUser().getRoomUnit().getCurrentLocation().x == this.getX()
                        && client.getUser().getRoomUnit().getCurrentLocation().y == this.getY())) {
            this.handle(room, client.getUser().getRoomUnit());
        }
    }

    protected void handle(Room room, RoomUnit roomUnit) {
        if (this.getExtradata().isEmpty()) this.setExtradata("0");

        if (!this.getExtradata().equals("0")) return;

        UserItem instance = this;
        room.giveHandItem(roomUnit, this.getBaseItem().getRandomVendingItem());
        polaris.hotel.users.User drinker = room.getUser(roomUnit);
        if (drinker != null) {
            polaris.hotel.quests.QuestProgressEvents.progress(
                    drinker, polaris.hotel.quests.QuestGoalType.GRAB_HAND_ITEM, 1);
        }

        if (this.getBaseItem().getStateCount() > 1) {
            this.setExtradata("1");
            room.updateItem(this);

            Emulator.getThreading()
                    .run(
                            () -> {
                                InteractionHanditem.this.setExtradata("0");
                                room.updateItem(instance);
                            },
                            500);
        }
    }
}
