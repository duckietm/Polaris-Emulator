package com.srth.plugin.interactions.vidayhambre;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;

import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;

import com.eu.habbo.messages.ServerMessage;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Tutorial.HabboTutorial;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.Habbo.HabboClientManager;
import com.srth.plugin.websocket.communication.outgoing.messages.ShowConfirmAlert;

import java.sql.ResultSet;
import java.sql.SQLException;

public class InteractionFoodsComer extends HabboItem {

    public InteractionFoodsComer(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    public InteractionFoodsComer(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        serverMessage.appendInt((this.isLimited() ? 256 : 0));
        serverMessage.appendString(this.getExtradata());

        super.serializeExtradata(serverMessage);
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return true;
    }

    @Override
    public boolean isWalkable() {
        return false;
    }

    @Override
    public void onClick(GameClient gameclient, Room room, Object[] objects) throws Exception {
        super.onClick(gameclient, room, objects);

        HabboClient client = HabboClientManager.getHabboClient(gameclient.getHabbo());

        if (room.getCategory() != RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.category.roleplay.id")) {
            return;
        }

        if (gameclient != null) {

            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(gameclient.getHabbo());

            HabboTutorial habboTutorial = habboRp.getHabboTutorial();
            if (habboTutorial.getParteTutorial() < 10) {
                return;
            }

            if (RoomLayout.tilesAdjecent(room.getLayout().getTile(this.getX(), this.getY()),
                    gameclient.getHabbo().getRoomUnit().getCurrentLocation())) {

                if (habboRp.getVidaHambreDormir().getHambre() >= 100) {
                    gameclient.getHabbo().whisper(
                            RoleplayWebsocketsRoomsInfo.getTexts().getValue("foods_machine.hambre.full"),
                            RoomChatMessageBubbles.NORMAL);
                    return;
                }

                if (habboRp.getProgressPendiente()) {
                    return;
                }

                HabboItem item = this;

                String estructura = "item_id:%item_id%;";
                String total_string = estructura.replace("%item_id%", item.getId() + "");

                if (habboRp.checkSettings("global_confirm_data", total_string) != "none") {
                    habboRp.saveToDatabase(total_string, "global_confirm_data");
                }

                habboRp.allowMove(false);

                // Descongelar
                Emulator.getThreading().run(() -> {
                    habboRp.allowMove(true);
                }, 1500);

                // Mostrar confirmación
                client.send(new ShowConfirmAlert("ShowConfirmAlert", "comer_platillo", 1, 120, true, ""));

            }
        }
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {

    }

    @Override
    public void onPickUp(Room room) {
        this.setExtradata("0");
    }

    @Override
    public boolean allowWiredResetState() {
        return false;
    }

    @Override
    public boolean isUsable() {
        return true;
    }

    @Override
    public void onMove(Room room, RoomTile oldLocation, RoomTile newLocation) {
    }
}
