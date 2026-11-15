package com.srth.plugin.habbo.ConfirmEvents.confirmselectevents;

import java.sql.SQLException;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.Habbo.HabboClientManager;
import com.srth.plugin.websocket.communication.outgoing.messages.outworks.PlaySound;

import gnu.trove.map.hash.THashMap;

public class SelectOrdenItemsIngredientes {

    public SelectOrdenItemsIngredientes(String name, int id, Habbo habbo) {

        // ingredientes_orden_select

        String[] getDataItem = RoleplayWebsocketsRoomsInfo.getTexts().getValue("window_" + name + "_item_" + id)
                .replace(";", ":").split(":");
        String[] getDataTexts = RoleplayWebsocketsRoomsInfo.getTexts().getValue("window_" + name + "_texts")
                .replace(";", ":").split(":");

        String clean_mount = getDataItem[5].replace("cash", "");
        int mount_total = Integer.parseInt(clean_mount.replace(" ", ""));
        int items_cantidad = Integer.parseInt(getDataItem[7].replace(" ", ""));

        int amount = mount_total;
        int type_moneda = RoleplayWebsocketsRoomsInfo.getConfig().getInt("seasonal.rol.primary.type");

        String item_name = getDataItem[9].replace(" ", "").replace(".png", "");
        if (!item_name.contains("ingredientes_variados")) {
            try {
                HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
                if (habboRp.getHabboTutorial().getParteTutorial() == 4) {
                    habboRp.showMessage("RoleplayWebsocketsRoomsInfo/tutorial/ingredientes_variados.png",
                            "Estas intentando comprar otra cosa, debes comprar ingredientes variados para continuar el tutorial...");
                    return;
                }
            } catch (Exception e) {
            }
        }

        if (item_name.contains("ingredientes_variados") && items_cantidad <= 6) {
            try {
                HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
                if (habboRp.getHabboTutorial().getParteTutorial() == 4) {
                    habboRp.showMessage("RoleplayWebsocketsRoomsInfo/tutorial/ingredientes_variados.png",
                            "Debes comprar minimo 10 ingredientes variados para continuar el tutorial...");
                    return;
                }
            } catch (Exception e) {
            }
        }

        // Mostrar alerta
        THashMap<String, String> keys3 = new THashMap<>();
        keys3.put("display", "BUBBLE");
        keys3.put("image", "${image.library.url}notifications/RoleplayWebsocketsRoomsInfo/tutorial/"
                + getDataItem[9].replace(" ", ""));
        keys3.put("message",
                getDataTexts[1].replace("%cantidad%", items_cantidad + "").replace("%precio%", mount_total + ""));
        habbo.getClient().sendResponse(new BubbleAlertComposer(BubbleAlertKeys.RECEIVED_BADGE.key, keys3));

        habbo.shout(RoleplayWebsocketsRoomsInfo.getTexts().getValue("action_buy_productos.success")
                .replace("%cantidad%", items_cantidad + "")
                .replace("%producto%", RoleplayWebsocketsRoomsInfo.getTexts().getValue("ingredients_name_global"))
                .replace("%amount%", mount_total + "")
                .replace("%type%", RoleplayWebsocketsRoomsInfo.getTexts().getValue("seasonal.name.rol")));

        // Comprobar monedas
        if (RoleplayWebsocketsRoomsInfo.getConfig().getBoolean("seasonal.rol.primary.type.its.credits") == true) {
            if (habbo.getHabboInfo().getCredits() < amount) {
                habbo.whisper(
                        RoleplayWebsocketsRoomsInfo.getTexts().getValue("action_cocinar.ingredientes.buy.no.currency"),
                        RoomChatMessageBubbles.NORMAL);
                return;
            }
        } else {
            if (habbo.getHabboInfo().getCurrencyAmount(type_moneda) < amount) {
                habbo.whisper(
                        RoleplayWebsocketsRoomsInfo.getTexts().getValue("action_cocinar.ingredientes.buy.no.currency"),
                        RoomChatMessageBubbles.NORMAL);
                return;
            }
        }

        HabboClient client = HabboClientManager.getHabboClient(habbo);
        try {
            client.send(new PlaySound("PlaySound", true, "buy.mp3", habbo));
        } catch (SQLException e) {
            e.printStackTrace();
        }

        try {

            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

            // Insertar tarea
            habboRp.getTareas().insertProgress("ComprarEnSupermercado");

            // Insertar ITEM en inventarioRP
            if (habboRp.getNewInventoryHabbo().getItem(item_name) == null) {
                habboRp.getNewInventoryHabbo().createItem(item_name, items_cantidad);
            } else {
                habboRp.getNewInventoryHabbo().addCantidadItem(item_name, items_cantidad);
                
            }

            // Verificar progreso en tutorial
            RoleplayWebsocketsRoomsInfo.getTutorialHabboRpManager().getEventos().eventBuyIngredientes(habbo);

            // Muestra el inventario
            // habboRp.getNewInventoryHabbo().showWindowInventory(0);

        } catch (SQLException e) {
            e.printStackTrace();
        }

        this.restarMonedas(habbo.getClient(), amount);
    }

    public void restarMonedas(GameClient client, int cantidad) {
        int amount = cantidad;
        int type_moneda = RoleplayWebsocketsRoomsInfo.getConfig().getInt("seasonal.rol.primary.type");

        if (RoleplayWebsocketsRoomsInfo.getConfig().getBoolean("seasonal.rol.primary.type.its.credits") == true) {
            if (client.getHabbo().getHabboInfo().getCredits() >= amount) {
                client.getHabbo().giveCredits(-amount);
            } else {
                client.getHabbo().whisper(
                        RoleplayWebsocketsRoomsInfo.getTexts().getValue("action_cocinar.ingredientes.buy.no.currency"),
                        RoomChatMessageBubbles.NORMAL);
                return;
            }

        } else {
            if (client.getHabbo().getHabboInfo().getCurrencyAmount(type_moneda) >= amount) {
                client.getHabbo().givePoints(type_moneda, -amount);
            } else {
                client.getHabbo().whisper(
                        RoleplayWebsocketsRoomsInfo.getTexts().getValue("action_cocinar.ingredientes.buy.no.currency"),
                        RoomChatMessageBubbles.NORMAL);
                return;
            }
        }
    }

}
