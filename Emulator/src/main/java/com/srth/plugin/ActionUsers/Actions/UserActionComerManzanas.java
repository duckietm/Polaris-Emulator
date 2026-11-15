package com.srth.plugin.ActionUsers.Actions;

import java.sql.SQLException;
import java.util.ArrayList;
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
import com.srth.plugin.habbo.Inventorys.InventoryHabboNew;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.Habbo.HabboClientManager;
import com.srth.plugin.websocket.communication.outgoing.messages.outworks.PlaySound;

import gnu.trove.map.hash.THashMap;

public class UserActionComerManzanas {

    public UserActionComerManzanas(Habbo habbo, int value, String type) throws SQLException {

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

        int amount = 1;
        int cantidad_comer = 0;
        String name_item = "";

        if (type.equals("rojas")) {
            name_item = "manzana_roja";
            cantidad_comer = 20;
        } else if (type.equals("verdes")) {
            name_item = "manzana_verde";
            cantidad_comer = 10;
        }

        habboRp.getVidaHambreDormir().comer(cantidad_comer);
        habboRp.getVidaHambreDormir().ignoreHambre(3);

        if (habboRp.getVidaHambreDormir().getHambre() >= 100) {
            habboRp.setCuracionPendiente(false);
        }

        InventoryHabboNew inventoryHabboNew = habboRp.getNewInventoryHabbo();
        inventoryHabboNew.removeCantidadItem(name_item, 1);
        inventoryHabboNew.updateItemsUsed();
        inventoryHabboNew.showButtonsUsar(habbo);

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

        String formatearNameItem = name_item.replace("_", " ");
        int cantidad_inventario = inventoryHabboNew.getItem(name_item).getCantidad();

        // Mostrar alerta al usuario
        habboRp.showMessage("/RoleplayWebsocketsRoomsInfo/recursos/" + name_item + ".png",
                RoleplayWebsocketsRoomsInfo.getTexts().getValue("comer_objetos.success").replace("%br%", "\n")
                        .replace("%cantidad%", cantidad_inventario + "")
                        .replace("%nombre_comida%", "una " + formatearNameItem));

        HabboClient client = HabboClientManager.getHabboClient(habbo);
        try {
            client.send(new PlaySound("PlaySound", true, "comer.mp3", habbo));
        } catch (SQLException e) {
            e.printStackTrace();
        }

        new comprobateTaskForAction(this.getClass().getSimpleName(), habbo);

        return;

    }

}