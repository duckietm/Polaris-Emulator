package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.interfaces.ConditionalGate;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.generic.alerts.CustomNotificationComposer;
import com.eu.habbo.threading.runnables.CloseGate;

import java.sql.ResultSet;
import java.sql.SQLException;

public class InteractionHabboClubGate extends InteractionDefault implements ConditionalGate {
    public InteractionHabboClubGate(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.setExtradata("0");
    }

    public InteractionHabboClubGate(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.setExtradata("0");
    }

    @Override
    public boolean isWalkable() {
        return true;
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        Habbo habbo = room.getHabbo(roomUnit);

        return habbo != null && habbo.getHabboStats().hasActiveClub();
    }

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOn(roomUnit, room, objects);

        if (this.canWalkOn(roomUnit, room, objects)) {
            this.setExtradata("1");
            room.updateItemState(this);
        }
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (client != null) {
            if (this.canWalkOn(client.getHabbo().getRoomUnit(), room, null)) {
                super.onClick(client, room, objects);
            } else {
                client.sendResponse(new CustomNotificationComposer(CustomNotificationComposer.GATE_NO_HC));
            }
        }
    }

    @Override
    public void onWalkOff(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOff(roomUnit, room, objects);

        Emulator.getThreading().run(new CloseGate(this, room), 1000);
    }

    @Override
    public void onRejected(RoomUnit roomUnit, Room room, Object[] objects) {
        if (roomUnit == null || room == null)
            return;

        room.getHabbo(roomUnit).getClient().sendResponse(
                new CustomNotificationComposer(CustomNotificationComposer.GATE_NO_HC)
        );
    }
}
