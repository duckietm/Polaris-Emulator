package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;

public class MannequinSaveLookEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        Room room = habbo.getHabboInfo().getCurrentRoom();

        if (room == null || !room.isOwner(habbo))
            return;

        int itemId = this.packet.readInt();
        if (!RoomItemInputGuard.isPositiveId(itemId))
            return;

        HabboItem item = room.getHabboItem(itemId);
        if (item == null)
            return;

        String[] data = item.getExtradata().split(":");
        // Extract only clothing parts, excluding head/face features (hr, hd, he, ea, ha, fa)

        StringBuilder look = new StringBuilder();

        for (String s : habbo.getHabboInfo().getLook().split("\\.")) {
            if (!s.contains("hr") && !s.contains("hd") && !s.contains("he") && !s.contains("ea") && !s.contains("ha") && !s.contains("fa")) {
                look.append(s).append(".");
            }
        }

        if (look.length() > 0) {
            look = new StringBuilder(look.substring(0, look.length() - 1));
        }

        if (data.length == 3) {
            item.setExtradata(habbo.getHabboInfo().getGender().name().toLowerCase() + ":" + look + ":" + data[2]);
        } else {
            item.setExtradata(habbo.getHabboInfo().getGender().name().toLowerCase() + ":" + look + ":" + habbo.getHabboInfo().getUsername() + "'s look.");
        }

        item.needsUpdate(true);
        Emulator.getThreading().run(item);
        room.updateItem(item);
    }
}
