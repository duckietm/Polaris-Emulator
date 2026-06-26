package com.eu.habbo.messages.incoming.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.plugin.events.furniture.FurnitureRoomTonerEvent;

public class RoomBackgroundEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int itemId = this.packet.readInt();

        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null)
            return;

        if (room.hasRights(this.client.getHabbo()) || this.client.getHabbo().hasPermission(Permission.ACC_PLACEFURNI)) {
            HabboItem item = room.getHabboItem(itemId);

            if (item == null)
                return;

            int hue = this.packet.readInt();
            int saturation = this.packet.readInt();
            int brightness = this.packet.readInt();

            var event = (FurnitureRoomTonerEvent) Emulator.getPluginManager().fireEvent(new FurnitureRoomTonerEvent(item, this.client.getHabbo(), hue, saturation, brightness));

            if (event.isCancelled())
                return;

            hue = event.hue % 256;
            saturation = event.saturation % 256;
            brightness = event.brightness % 256;

            item.setExtradata(item.getExtradata().split(":")[0] + ":" + hue + ":" + saturation + ":" + brightness);
            item.needsUpdate(true);
            Emulator.getThreading().run(item);
            room.updateItem(item);
        }
    }
}
