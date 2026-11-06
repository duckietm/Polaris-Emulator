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
import polaris.messages.ServerMessage;
import polaris.plugin.events.furniture.FurnitureDiceRolledEvent;
import polaris.threading.runnables.RandomDiceNumber;

public class InteractionDice extends UserItem {
    public InteractionDice(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    public InteractionDice(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
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

        if (polaris.hotel.rooms.RoomDiceDisableSupport.isActive(room)) return;

        if (client != null) {
            if (RoomLayout.tilesAdjecent(
                    room.getLayout().getTile(this.getX(), this.getY()),
                    client.getUser().getRoomUnit().getCurrentLocation())) {
                if (!this.getExtradata().equalsIgnoreCase("-1")) {
                    FurnitureDiceRolledEvent event = (FurnitureDiceRolledEvent) Emulator.getPluginManager()
                            .fireEvent(new FurnitureDiceRolledEvent(this, client.getUser(), -1));

                    if (event.isCancelled()) return;

                    this.setExtradata("-1");
                    room.updateItemState(this);
                    Emulator.getThreading().run(this);
                    // A roll changes the dice's state: "state changed" fires, as in Habbo.
                    notifyStateChanged(room, client.getUser().getRoomUnit(), this);

                    if (event.result > 0) {
                        Emulator.getThreading().run(new RandomDiceNumber(room, this, event.result), 1500);
                    } else {
                        Emulator.getThreading()
                                .run(
                                        new RandomDiceNumber(
                                                this, room, this.getBaseItem().getStateCount()),
                                        1500);
                    }
                }
            }
        }
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {}

    @Override
    public void onPickUp(Room room) {
        this.setExtradata("0");
    }

    @Override
    public boolean allowWiredResetState() {
        return false;
    }

    @Override
    public boolean isUsable() {
        return true;
    }
}
