package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;

public class MannequinSaveNameEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null || !room.isOwner(this.client.getHabbo()))
            return;

        int itemId = this.packet.readInt();
        if (!RoomItemInputGuard.isPositiveId(itemId))
            return;

        HabboItem item = room.getHabboItem(itemId);
        if (item == null)
            return;

        String[] data = item.getExtradata().split(":");
        String name = RoomItemInputGuard.trimToMax(this.packet.readString(), 32);

        if (name.length() < 3 || name.length() > 15) {
            name = Emulator.getTexts().getValue("hotel.mannequin.name.default", "My look");
        }

        if (data.length == 3) {
            item.setExtradata(this.client.getHabbo().getHabboInfo().getGender().name().toUpperCase() + ":" + data[1] + ":" + name);
        } else {
            item.setExtradata(this.client.getHabbo().getHabboInfo().getGender().name().toUpperCase() + ":" + this.client.getHabbo().getHabboInfo().getLook() + ":" + name);
        }
        item.needsUpdate(true);
        Emulator.getThreading().run(item);
        room.updateItem(item);
    }
}
