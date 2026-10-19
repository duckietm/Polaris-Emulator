package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraLabel;
import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.label.WiredLabelSupport;
import com.eu.habbo.messages.incoming.MessageHandler;

/** Official furniture label text save (AIR 2638): the furni id and the new text. */
public class WiredLabelSaveTextEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int furniId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
        String text = this.packet.readString();
        Room room = currentRoom();

        if (room == null || !room.canModifyWired(this.client.getHabbo())) return;

        HabboItem item = room.getHabboItem(furniId);
        if (!(item instanceof WiredExtraLabel label)) return;

        label.setText(text);
        WiredLabelSupport.save(label);
        WiredLabelSupport.broadcastChanged(room, label);
    }

    @Override
    public int getRatelimit() {
        return 250;
    }
}
