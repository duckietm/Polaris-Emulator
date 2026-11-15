package com.srth.plugin.events.habboHandlers;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ClientMessage;
import com.eu.habbo.messages.ICallable;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.ActionUsers.ManagerClickHabbos;

public class handledFurniClick implements ICallable {

    private int increment = 0;
    GameClient client;

    @Override
    public void call(MessageHandler event) {

        this.client = event.client;

        ClientMessage packetCopy;
        int furni = event.packet.readInt(); // si se obtiene segundo parametro
        try {
            packetCopy = event.packet.clone();
            furni = packetCopy.readInt();
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }

        increment++;
        if (increment >= 2) {
            increment = 0;
            return;
        }

        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null)
            return;

        HabboItem item = room.getHabboItem(furni);
        if (item == null)
            return;

        // Comprobar si se hace un click en estos items
        RoleplayWebsocketsRoomsInfo.getItemActionManager().eventReceibedSingleClickFurni(item, event.client.getHabbo());

        new ManagerClickHabbos().eventFurniClick(event.client.getHabbo());

    }
}