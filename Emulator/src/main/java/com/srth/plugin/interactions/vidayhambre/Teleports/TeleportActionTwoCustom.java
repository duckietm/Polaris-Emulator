package com.srth.plugin.interactions.vidayhambre.Teleports;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.interactions.InteractionTeleportTile;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;
import com.eu.habbo.threading.runnables.HabboItemNewState;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

class TeleportActionTwoCustom implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(TeleportActionTwoCustom.class);

    private final HabboItem currentTeleport;
    private final Room room;
    private final GameClient client;

    public TeleportActionTwoCustom(HabboItem currentTeleport, Room room, GameClient client) {
        this.currentTeleport = currentTeleport;
        this.client = client;
        this.room = room;
    }

    @Override
    public void run() {
        int delayOffset = 200;

        if (this.currentTeleport instanceof InteractionTeleportTile) {
            delayOffset = 0;
        }

        if (this.client.getHabbo().getHabboInfo().getCurrentRoom() != this.room)
            return;

        this.client.getHabbo().getRoomUnit().removeStatus(RoomUnitStatus.MOVE);
        this.room.sendComposer(new RoomUserStatusComposer(this.client.getHabbo().getRoomUnit()).compose());

        if (((InteractionTeleports) this.currentTeleport).getTargetRoomId() > 0
                && ((InteractionTeleports) this.currentTeleport).getTargetId() > 0) {
            HabboItem item = this.room.getHabboItem(((InteractionTeleports) this.currentTeleport).getTargetId());
            if (item == null) {
                ((InteractionTeleports) this.currentTeleport).setTargetRoomId(0);
                ((InteractionTeleports) this.currentTeleport).setTargetId(0);
            } else if (((InteractionTeleports) item).getTargetRoomId() != ((InteractionTeleports) this.currentTeleport)
                    .getTargetRoomId()) {
                ((InteractionTeleports) this.currentTeleport).setTargetId(0);
                ((InteractionTeleports) this.currentTeleport).setTargetRoomId(0);
                ((InteractionTeleports) item).setTargetId(0);
                ((InteractionTeleports) item).setTargetRoomId(0);
            }
        } else {
            ((InteractionTeleports) this.currentTeleport).setTargetRoomId(0);
            ((InteractionTeleports) this.currentTeleport).setTargetId(0);
        }
        if (((InteractionTeleports) this.currentTeleport).getTargetId() == 0) {

            int roomID = 0;
            if (this.room.getId() == RoleplayWebsocketsRoomsInfo.getRoomsRoleplayManager().getRoom("calle_del_puente")
                    .getRoomId()) {
                try {
                    HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager()
                            .getHabboRp(this.client.getHabbo());
                    if (RoleplayWebsocketsRoomsInfo.getControllerRecorridosTaxi()
                            .getRecorridoTaxi(this.client.getHabbo()) != null) {
                        if (RoleplayWebsocketsRoomsInfo.getControllerRecorridosTaxi()
                                .getRecorridoTaxi(this.client.getHabbo()).getRoomObjetive().getName()
                                .contains("[CASA]")) {
                            roomID = RoleplayWebsocketsRoomsInfo.getControllerRecorridosTaxi()
                                    .getRecorridoTaxi(this.client.getHabbo()).getRoomObjetive().getId();
                        } else {
                            roomID = habboRp.getInt("global.home.room.rp.id");
                        }
                    } else {
                        roomID = habboRp.getInt("global.home.room.rp.id");
                    }
                } catch (Exception e) {
                }
            } else {
                roomID = RoleplayWebsocketsRoomsInfo.getRoomsRoleplayManager().getRoom("calle_del_puente")
                        .getRoomId();
            }
            try {

                Room roomObjetive = Emulator.getGameEnvironment().getRoomManager().loadRoom(roomID);
                roomObjetive.loadData();
                roomObjetive.run();

                if (roomObjetive != null) {
                    HabboItem objetive = null;
                    for (HabboItem iterable_element : roomObjetive.getFloorItems()) {
                        if (!iterable_element.getBaseItem().getName().equalsIgnoreCase("room_switcher_home")) {
                            continue;
                        }
                        objetive = iterable_element;
                    }
                    if (objetive != null) {
                        ((InteractionTeleports) this.currentTeleport).setTargetId(objetive.getId());
                        ((InteractionTeleports) this.currentTeleport).setTargetRoomId(roomObjetive.getId());
                    } else {
                        this.client.getHabbo().whisper("La casa a la que quieres entra no tiene flechas de entrada");
                        Emulator.getThreading()
                                .run(new TeleportActionFiveCustom(this.currentTeleport, this.room, this.client), 0);
                        return;
                    }
                }

            } catch (Exception e) {
            }

        }

        this.currentTeleport.setExtradata("0");
        this.room.updateItem(this.currentTeleport);

        if (((InteractionTeleports) this.currentTeleport).getTargetRoomId() == 0) {
            // Emulator.getThreading().run(new HabboItemNewState(this.currentTeleport, room,
            // "1"), 0);
            Emulator.getThreading().run(new TeleportActionFiveCustom(this.currentTeleport, this.room, this.client), 0);
            return;
        }

        Emulator.getThreading().run(new HabboItemNewState(this.currentTeleport, this.room, "2"), delayOffset);
        Emulator.getThreading().run(new HabboItemNewState(this.currentTeleport, this.room, "0"), delayOffset + 1000);
        Emulator.getThreading().run(new TeleportActionThreeCustom(this.currentTeleport, this.room, this.client),
                delayOffset);
    }
}
