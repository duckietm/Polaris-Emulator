package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.interfaces.ConditionalGate;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.messages.outgoing.generic.alerts.CustomNotificationComposer;
import polaris.threading.runnables.CloseGate;

public class InteractionClubGate extends InteractionDefault implements ConditionalGate {
    public InteractionClubGate(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.setExtradata("0");
    }

    public InteractionClubGate(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.setExtradata("0");
    }

    @Override
    public boolean isWalkable() {
        return true;
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        User user = room.getUser(roomUnit);

        return user != null && user.getUserStats().hasActiveClub();
    }

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOn(roomUnit, room, objects);

        if (this.canWalkOn(roomUnit, room, objects)) {
            this.setExtradata("1");
            room.updateItemState(this);
        }
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (client != null) {
            if (this.canWalkOn(client.getUser().getRoomUnit(), room, null)) {
                super.onClick(client, room, objects);
            } else {
                client.sendResponse(new CustomNotificationComposer(CustomNotificationComposer.GATE_NO_HC));
            }
        }
    }

    @Override
    public void onWalkOff(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOff(roomUnit, room, objects);

        Emulator.getThreading().run(new CloseGate(this, room), 1000);
    }

    @Override
    public void onRejected(RoomUnit roomUnit, Room room, Object[] objects) {
        if (roomUnit == null || room == null) return;

        room.getUser(roomUnit)
                .getClient()
                .sendResponse(new CustomNotificationComposer(CustomNotificationComposer.GATE_NO_HC));
    }
}
