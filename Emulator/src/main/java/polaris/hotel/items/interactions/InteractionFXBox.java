package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserGender;
import polaris.hotel.users.UserItem;
import polaris.messages.outgoing.rooms.items.RemoveFloorItemComposer;
import polaris.threading.runnables.QueryDeleteUserItem;

public class InteractionFXBox extends InteractionDefault {
    public InteractionFXBox(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        //   this.setExtradata("0");
    }

    public InteractionFXBox(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        // this.setExtradata("0");
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (client != null && this.getUserId() == client.getUser().getUserInfo().getId()) {
            if (this.getExtradata().equals("1")) return;

            int effectId = -1;

            if (client.getUser().getUserInfo().getGender().equals(UserGender.M)) {
                if (this.getBaseItem().getEffectM() > 0) {
                    effectId = this.getBaseItem().getEffectM();
                }
            }

            if (client.getUser().getUserInfo().getGender().equals(UserGender.F)) {
                if (this.getBaseItem().getEffectF() > 0) {
                    effectId = this.getBaseItem().getEffectF();
                }
            }

            if (effectId < 0) return;

            if (client.getUser().getInventory().getEffectsComponent().ownsEffect(effectId)) return;

            client.getUser().getInventory().getEffectsComponent().createEffect(effectId, 0);
            client.getUser().getInventory().getEffectsComponent().enableEffect(effectId);

            this.setExtradata("1");
            room.updateItemState(this);
            room.removeUserItem(this);
            UserItem item = this;
            Emulator.getThreading()
                    .run(
                            () -> {
                                new QueryDeleteUserItem(item.getId()).run();
                                room.sendComposer(new RemoveFloorItemComposer(item).compose());
                                room.updateTile(room.getLayout().getTile(this.getX(), this.getY()));
                            },
                            500);
        }
    }
}
