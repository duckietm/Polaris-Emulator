package com.srth.plugin.events.habboHandlers;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ClientMessage;
import com.eu.habbo.messages.ICallable;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.ActionUsers.ManagerClickHabbos;
import com.srth.plugin.habbo.HabboRp;

public class handlerUserClickTile implements ICallable {

    GameClient client;

    @Override
    public void call(MessageHandler event) {

        this.client = event.client;

        ClientMessage packetCopy;
        int x = 0;
        int y = 0;
        try {
            packetCopy = event.packet.clone();

            x = packetCopy.readInt();
            y = packetCopy.readInt();

        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }

        Habbo habbo = event.client.getHabbo();
        RoomUnit roomUnit = habbo.getRoomUnit();

        if (x == roomUnit.getX() && y == roomUnit.getY() || habbo.getHabboInfo().getCurrentRoom() == null) {
            return;
        }

        RoomTile tile = habbo.getHabboInfo().getCurrentRoom().getLayout().getTile((short) x, (short) y);

        if (tile != null) {
            roomUnit.lookAtPoint(tile);
            roomUnit.statusUpdate(true);
            // room.sendComposer(new RoomUserStatusComposer(roomUnit).compose());

            if (tile.isWalkable() == true || tile.state == RoomTileState.OPEN || tile.state == RoomTileState.SIT) {

                RoleplayWebsocketsRoomsInfo.getRoomsRoleplayManager().eventUserClickTile(event);
                RoleplayWebsocketsRoomsInfo.getControllerRecorridosTaxi().eventUserMove(event);
                RoleplayWebsocketsRoomsInfo.getCofreInventoryManager().ocultInventoryToClientCofre(habbo);

                try {
                    HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
                    new ManagerClickHabbos().ocultOtherInfo(habboRp.getHabboClient());
                    new ManagerClickHabbos().ocultButtonsUsarOther(habboRp.getHabboClient());
                } catch (Exception e) {
                }

            }
        }

    }
}