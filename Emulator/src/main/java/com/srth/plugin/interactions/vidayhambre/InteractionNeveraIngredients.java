package com.srth.plugin.interactions.vidayhambre;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;

import com.eu.habbo.messages.ServerMessage;

import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;

import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Tutorial.HabboTutorial;
import com.srth.plugin.habbo.HabboCocinar;
import com.srth.plugin.habbo.HabboRp;

import java.sql.ResultSet;
import java.sql.SQLException;

import gnu.trove.map.hash.THashMap;

public class InteractionNeveraIngredients extends HabboItem {

    private String name_item;

    public InteractionNeveraIngredients(int id, int userId, Item item, String extradata, int limitedStack,
            int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    public InteractionNeveraIngredients(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.name_item = baseItem.getName();
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
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        super.onClick(client, room, objects);

        this.name_item = this.getBaseItem().getName();

        if (client != null) {
            if (room.getCategory() != RoleplayWebsocketsRoomsInfo.getConfig()
                    .getInt("global_room.category.roleplay.id")) {
                return;
            }

            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(client.getHabbo());

            HabboCocinar userCocinar = new HabboCocinar(client.getHabbo());

            if (RoomLayout.tilesAdjecent(room.getLayout().getTile(this.getX(), this.getY()),
                    client.getHabbo().getRoomUnit().getCurrentLocation())) {

                if (this.getUserId() != client.getHabbo().getHabboInfo().getId()) {
                    return;
                }

                if (habboRp.getNewInventoryHabbo().getItem("ingredientes_variados") == null
                        || habboRp.getNewInventoryHabbo().getItem("ingredientes_variados") != null
                                && habboRp.getNewInventoryHabbo().getItem("ingredientes_variados").getCantidad() == 0) {
                    habboRp.showMessage("/RoleplayWebsocketsRoomsInfo/tutorial/ingredientes_variados.png",
                            RoleplayWebsocketsRoomsInfo.getTexts().getValue("action_cocinar.no.suf.ingredients"));
                    return;
                }

                if (userCocinar.getCocinando() == 1) {

                    if (userCocinar.getIngredientes() < 3) {
                        THashMap<String, String> notification = new THashMap<>();
                        notification.put("display", "BUBBLE");
                        notification.put("image",
                                "${image.library.url}notifications/RoleplayWebsocketsRoomsInfo/tutorial/ingredientes_variados.png");
                        notification.put("linkUrl", "event:navigator/goto/" + RoleplayWebsocketsRoomsInfo.getConfig()
                                .getInt("global_room_normales.supermercado.id"));
                        notification.put("message",
                                RoleplayWebsocketsRoomsInfo.getTexts().getValue("action_cocinar.no.suf.ingredients2"));
                        client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_BADGE.key, notification));
                        return;
                    }

                    if (habboRp.getProgressPendiente() == true) {
                        return;
                    }

                    if (userCocinar.getCocinando() == 2) {

                        RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlert(
                                "RoleplayWebsocketsRoomsInfo/cocinero/ktchn_stove_icon.png",
                                "action_nevera.ingredients.mano", client);

                        return;
                    }

                    HabboTutorial habboTutorial = habboRp.getHabboTutorial();
                    if (habboTutorial.getParteTutorial() < 8) {
                        return;
                    }

                    if (userCocinar.getCocinando() == 1) {

                        THashMap<String, String> keys = new THashMap<>();
                        // Mostrar proceso
                        userCocinar.showTimeProcess("RoleplayWebsocketsRoomsInfo/cocinero/" + name_item + "_icon.png",
                                "Tomando ingredientes:", 6, true);
                        userCocinar.setCocinando(2);

                        this.setExtradata("1");
                        this.needsUpdate(true);
                        room.updateItemState(this);
                        room.updateItem(this);
                        Emulator.getThreading().run(this);

                        habboRp.allowMove(false);
                        habboRp.setProgressPendiente(true);

                        Emulator.getThreading().run(() -> {

                            keys.put("display", "BUBBLE");
                            keys.put("image",
                                    "${image.library.url}notifications/RoleplayWebsocketsRoomsInfo/cocinero/rp_food_ktchn_icon.png");
                            keys.put("message",
                                    RoleplayWebsocketsRoomsInfo.getTexts().getValue("action_cocinar.select.success")
                                            .replace("%br%", "\n").replace("%select%", userCocinar.getPlatillo()));
                            client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_BADGE.key, keys));

                            userCocinar.setCocinando(2);

                        }, 6000);

                        Emulator.getThreading().run(() -> {

                            if (habboRp.checkSettings("tutorial_active", 0) == 0) {
                                keys.put("display", "BUBBLE");
                                keys.put("image",
                                        "${image.library.url}notifications/RoleplayWebsocketsRoomsInfo/cocinero/rp_nevera_icon.png");
                                keys.put("message", RoleplayWebsocketsRoomsInfo.getTexts()
                                        .getValue("action_cocinar.ingredients.success").replace("%br%", "\n"));
                                client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_BADGE.key, keys));
                            }

                            this.setExtradata("0");
                            this.needsUpdate(true);
                            room.updateItemState(this);
                            room.updateItem(this);
                            Emulator.getThreading().run(this);

                            habboRp.allowMove(true);
                            habboRp.setProgressPendiente(false);

                            RoleplayWebsocketsRoomsInfo.getTutorialHabboRpManager().getEventos().eventCocinar(2,
                                    client.getHabbo());

                            // tutorialHabbo.checkTutorialProcessSuccess(this.getBaseItem());

                        }, 7500);

                    }

                } else {
                }
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
