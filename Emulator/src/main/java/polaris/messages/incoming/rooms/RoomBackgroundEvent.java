package polaris.messages.incoming.rooms;

import polaris.Emulator;
import polaris.hotel.items.interactions.InteractionBackgroundToner;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomPlayTestSupport;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;
import polaris.plugin.events.furniture.FurnitureRoomTonerEvent;

public class RoomBackgroundEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());

        Room room = this.client.getUser().getUserInfo().getCurrentRoom();
        if (room == null) return;

        // Playtest (the hotel's own rule beyond AIR): the toner is not set up, as for a visitor without rights.
        if (RoomPlayTestSupport.isBuildingRefused(this.client.getUser())) return;

        if (room.hasRights(this.client.getUser()) || this.client.getUser().hasPermission(Permission.ACC_PLACEFURNI)) {
            UserItem item = room.getUserItem(itemId);

            // Only a toner takes a colour: any other furni's state (a gate, a trophy, a stack helper)
            // was overwritten with one by anyone holding rights.
            if (!(item instanceof InteractionBackgroundToner)) return;

            int hue = this.packet.readInt();
            int saturation = this.packet.readInt();
            int brightness = this.packet.readInt();

            FurnitureRoomTonerEvent event = (FurnitureRoomTonerEvent) Emulator.getPluginManager()
                    .fireEvent(new FurnitureRoomTonerEvent(item, this.client.getUser(), hue, saturation, brightness));

            if (event.isCancelled()) return;

            // floorMod: a negative value from the packet stays in 0-255 instead of going negative.
            hue = Math.floorMod(event.hue, 256);
            saturation = Math.floorMod(event.saturation, 256);
            brightness = Math.floorMod(event.brightness, 256);

            item.setExtradata(item.getExtradata().split(":")[0] + ":" + hue + ":" + saturation + ":" + brightness);
            item.needsUpdate(true);
            Emulator.getThreading().run(item);
            room.updateItem(item);
        }
    }
}
