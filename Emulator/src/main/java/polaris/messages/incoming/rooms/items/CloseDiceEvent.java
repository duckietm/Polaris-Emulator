package polaris.messages.incoming.rooms.items;

import polaris.Emulator;
import polaris.hotel.items.interactions.InteractionDice;
import polaris.hotel.modtool.ScripterManager;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomLayout;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;

public class CloseDiceEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());

        Room room = this.client.getUser().getUserInfo().getCurrentRoom();

        if (room == null) return;

        UserItem item = room.getUserItem(itemId);

        if (item != null) {
            if (item instanceof InteractionDice) {
                if (RoomLayout.tilesAdjecent(
                        room.getLayout().getTile(item.getX(), item.getY()),
                        this.client.getUser().getRoomUnit().getCurrentLocation())) {
                    if (!item.getExtradata().equals("-1")) {
                        item.setExtradata("0");
                        item.needsUpdate(true);
                        Emulator.getThreading().run(item);
                        room.updateItem(item);
                    }
                }
            } else {
                ScripterManager.scripterDetected(
                        this.client,
                        Emulator.getTexts()
                                .getValue("scripter.warning.packet.closedice")
                                .replace(
                                        "%username%",
                                        this.client.getUser().getUserInfo().getUsername())
                                .replace("%id%", item.getId() + "")
                                .replace("%itemname%", item.getBaseItem().getName()));
            }
        }
    }
}
