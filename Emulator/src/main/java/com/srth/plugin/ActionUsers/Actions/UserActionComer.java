package com.srth.plugin.ActionUsers.Actions;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.rooms.items.AddFloorItemComposer;
import com.eu.habbo.threading.runnables.QueryDeleteHabboItems;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Tutorial.HabboTutorial;
import com.srth.plugin.ActionUsers.comprobateTaskForAction;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.Habbo.HabboClientManager;
import com.srth.plugin.websocket.communication.outgoing.messages.outworks.PlaySound;

import gnu.trove.map.hash.THashMap;

public class UserActionComer {

    public UserActionComer(Habbo habbo, int value) throws SQLException {

        Room room = habbo.getHabboInfo().getCurrentRoom();

        if (room.getCategory() != RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.category.roleplay.id")) {
            habbo.whisper(RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_global.commandrp.room.no.rp"));
            return;
        }

        HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

        HabboTutorial habboTutorial = habboRp.getHabboTutorial();
        if (habboTutorial.getParteTutorial() == 10) {
            habboRp.showMessage("/RoleplayWebsocketsRoomsInfo/tutorial/comida.png",
                    RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_comer.hambre.no.permited.tuto"));
            return;
        }

        if (habboRp.getVidaHambreDormir().getHambre() >= 100) {

            habboRp.setCuracionPendiente(false);
            habboRp.showMessage("/RoleplayWebsocketsRoomsInfo/tutorial/comida.png",
                    RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_comer.hambre.full"));
            return;
        }

        if (habboRp.getCuracionPendiente()
                && room.getId() != RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.job_medico.id")) {
            RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlert("/RoleplayWebsocketsRoomsInfo/tutorial/comida.png",
                    "global_alert.no.accion.curacion.pendiente", habbo.getClient());
            habboRp.showMessage("/RoleplayWebsocketsRoomsInfo/tutorial/comida.png",
                    RoleplayWebsocketsRoomsInfo.getTexts().getValue("global_alert.no.accion.curacion.pendiente"));
            return;
        }

        if (habboRp.getProgressPendiente()) {
            RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlert(
                    "/RoleplayWebsocketsRoomsInfo/job_polices/work_no_accion.gif",
                    "global_alert.no.accion.progress.pendiente", habbo.getClient());
            return;
        }

        if (habboRp.getVidaHambreDormir().getHambre() >= 80) {
            // Insertar tarea
            habboRp.getTareas().insertProgress("ComerPlatillos");
        }

        String item_starts = "rp_food";
        int amount = 0;
        int index = 0;
        int cantidad_eliminar = value;
        Map<Integer, HabboItem> deleted = new HashMap<>();
        ArrayList<HabboItem> items = new ArrayList<>();
        for (HabboItem item : habbo.getInventory().getItemsComponent().getItemsAsValueCollection()) {
            if (item.getBaseItem().getName().startsWith(item_starts)) {
                if (item.getUserId() == habbo.getHabboInfo().getId()) {
                    if ((item.getBaseItem().getName().startsWith(item_starts))) {
                        try {
                            // Array eliminar un solo item
                            items.add(item);
                            amount += 1;
                            index++;
                            if (index <= cantidad_eliminar) {
                                habbo.getInventory().getItemsComponent().removeHabboItem(item);
                                habbo.getClient().sendResponse(new InventoryRefreshComposer());
                                deleted.put(item.getId(), item);
                                Emulator.getThreading().run(new QueryDeleteHabboItems(deleted.values()));
                            }
                        } catch (Exception e) {
                        }
                    }
                }
            }
        }

        if (amount >= 1) {

            habboRp.getVidaHambreDormir().comer(40);
            habboRp.getVidaHambreDormir().ignoreHambre(6);

            if (habboRp.getVidaHambreDormir().getHambre() >= 100) {
                habboRp.setCuracionPendiente(false);
            }

            Item rewardItem = Emulator.getGameEnvironment().getItemManager().getItem("rp_comido_food_ktchn");
            HabboItem newItem = Emulator.getGameEnvironment().getItemManager().createItem(habbo.getHabboInfo().getId(),
                    rewardItem, 0, 0, "");
            if (newItem != null) {
                newItem.setX(habbo.getRoomUnit().getX());
                newItem.setY(habbo.getRoomUnit().getY());
                newItem.setZ(room.getStackHeight(habbo.getRoomUnit().getX(), habbo.getRoomUnit().getY(), false));
                newItem.setRoomId(room.getId());
                newItem.needsUpdate(true);
                room.addHabboItem(newItem);
                room.updateItem(newItem);
                room.sendComposer(new AddFloorItemComposer(newItem, room.getFurniOwnerNames().get(newItem.getUserId()))
                        .compose());

                Item comprobate_item = Emulator.getGameEnvironment().getItemManager().getItem("rp_comido_food_ktchn");
                if (comprobate_item != null) {
                    // Insertar ITEM en inventarioRP
                    habboRp.getNewInventoryHabbo().addCantidadItem("rp_comido_food_ktchn", 1);
                }
            }

            // EVENTO DE PLATILLO COMIDO
            RoleplayWebsocketsRoomsInfo.getTutorialHabboRpManager().getEventos().eventComerFoodSecundary(habbo);

            // Mostrar alerta al usuario
            habboRp.showMessage("/RoleplayWebsocketsRoomsInfo/cocinero/rp_food_ktchn_icon.png",
                    RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_comer.success").replace("%br%", "\n")
                            .replace("%cantidad%", amount - 1 + ""));

            HabboClient client = HabboClientManager.getHabboClient(habbo);
            try {
                client.send(new PlaySound("PlaySound", true, "comer.mp3", habbo));
            } catch (SQLException e) {
                e.printStackTrace();
            }

            new comprobateTaskForAction(this.getClass().getSimpleName(), habbo);

            return;
        } else {

            // Mostrar alerta al usuario
            THashMap<String, String> keys2 = new THashMap<>();
            keys2.put("display", "BUBBLE");
            keys2.put("image",
                    "${image.library.url}notifications/RoleplayWebsocketsRoomsInfo/cocinero/rp_food_ktchn_icon.png");
            keys2.put("message",
                    RoleplayWebsocketsRoomsInfo.getTexts().getValue("cmd_comer.empty").replace("%br%", "\n"));
            habbo.getClient().sendResponse(new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_BADGE.key, keys2));

            return;
        }

    }

}