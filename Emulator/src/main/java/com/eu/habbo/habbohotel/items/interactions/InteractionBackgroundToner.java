package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.modtool.ScripterManager;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.threading.runnables.BackgroundAnimation;

import java.sql.ResultSet;
import java.sql.SQLException;

public class InteractionBackgroundToner extends HabboItem {
    public InteractionBackgroundToner(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionBackgroundToner(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        serverMessage.appendInt(5 + (this.isLimited() ? 256 : 0));
        serverMessage.appendInt(4);
        if (this.getExtradata().split(":").length == 4) {
            String[] colorData = this.getExtradata().split(":");
            serverMessage.appendInt(Integer.valueOf(colorData[0]));
            serverMessage.appendInt(Integer.valueOf(colorData[1]));
            serverMessage.appendInt(Integer.valueOf(colorData[2]));
            serverMessage.appendInt(Integer.valueOf(colorData[3]));
        } else {
            serverMessage.appendInt(0);
            serverMessage.appendInt(126);
            serverMessage.appendInt(126);
            serverMessage.appendInt(126);
            this.setExtradata("0:126:126:126");
            this.needsUpdate(true);
            Emulator.getThreading().run(this);
        }

        super.serializeExtradata(serverMessage);
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return true;
    }

    @Override
    public boolean isWalkable() {
        return this.getBaseItem().allowWalk();
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        super.onClick(client, room, objects);

        if(client != null)
        {
            if (!client.getHabbo().getRoomUnit().getRoom().hasRights(client.getHabbo())) {
                ScripterManager.scripterDetected(
                        client,
                        Emulator.getTexts().getValue("scripter.warning.item.bgtoner.permission").replace("%username%", client.getHabbo().getHabboInfo().getUsername())
                                .replace("%room%", room.getName())
                                .replace("%owner%", room.getOwnerName())
                );
                return;
            }
            
            if (client.getHabbo().getRoomUnit().cmdSit && client.getHabbo().getRoomUnit().getEffectId() == 1337) {
                new BackgroundAnimation(this, room).run();
                return;
            }
        }

        if (this.getExtradata().split(":").length == 4) {
            String[] data = this.getExtradata().split(":");
            this.setExtradata((data[0].equals("0") ? "1" : "0") + ":" + data[1] + ":" + data[2] + ":" + data[3]);
            room.updateItem(this);
        } else {
            this.setExtradata("0:126:126:126");
            room.updateItem(this);
        }
        this.needsUpdate(true);
        Emulator.getThreading().run(this);
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {

    }

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOn(roomUnit, room, objects);
    }

    @Override
    public void onWalkOff(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOff(roomUnit, room, objects);
    }

    @Override
    public boolean isUsable() {
        return true;
    }
}
