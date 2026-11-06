package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.UserItem;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.rooms.items.WallItemUpdateComposer;
import polaris.threading.runnables.RandomDiceNumber;

public class InteractionColorWheel extends UserItem {
    private Runnable rollTaks;

    public InteractionColorWheel(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionColorWheel(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        serverMessage.appendInt((this.isLimited() ? 256 : 0));
        serverMessage.appendString(this.getExtradata());

        super.serializeExtradata(serverMessage);
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return true;
    }

    @Override
    public boolean isWalkable() {
        return false;
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        super.onClick(client, room, objects);

        if (!room.hasRights(client.getUser())) return;

        if (this.rollTaks == null && !this.getExtradata().equalsIgnoreCase("-1")) {
            this.setExtradata("-1");
            room.sendComposer(new WallItemUpdateComposer(this).compose());
            Emulator.getThreading().run(this);
            Emulator.getThreading()
                    .run(new RandomDiceNumber(this, room, this.getBaseItem().getStateCount()), 3000);
        }
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {}

    @Override
    public void onWalkOn(RoomUnit client, Room room, Object[] objects) throws Exception {}

    @Override
    public void onWalkOff(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOff(roomUnit, room, objects);
    }

    public void clearRunnable() {
        this.rollTaks = null;
    }
}
