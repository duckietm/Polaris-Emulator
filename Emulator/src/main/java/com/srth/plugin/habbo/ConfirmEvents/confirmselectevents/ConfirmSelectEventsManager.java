package com.srth.plugin.habbo.ConfirmEvents.confirmselectevents;

import java.sql.SQLException;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Rooms.Cycleables.RoomCafeteria;
import com.srth.plugin.Rooms.Mapa.Actions.CreateRoomGenericRoleplay;
import com.srth.plugin.Rooms.Tyles.EventCreateConexionTiles;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.ConfirmEvents.confirmevents.ConfirmBuyElectrodomesticos;
import com.srth.plugin.habbo.ConfirmEvents.confirmevents.ConfirmEventsManager;
import com.srth.plugin.habbo.ConfirmEvents.confirmevents.ConfirmBuyPlatillosComidaSelect;
import com.srth.plugin.habbo.ConfirmEvents.confirmevents.EventConfirmBuyCanaPescar;
import com.srth.plugin.habbo.ConfirmEvents.confirmselectevents.utils.utilTestFunction;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.Habbo.HabboClientManager;
import com.srth.plugin.websocket.communication.Outcoming;
import com.srth.plugin.websocket.communication.outgoing.messages.MessageManager;
import com.srth.plugin.websocket.communication.outgoing.messages.ShowVentSelectConfirm;
import com.srth.plugin.websocket.communication.outgoing.messages.outworks.PlaySound;

import gnu.trove.map.hash.THashMap;

public class ConfirmSelectEventsManager {

    HabboClient clientws;

    public ConfirmSelectEventsManager() {

    }

    public void setHabbo(Habbo habbo) {
        this.clientws = HabboClientManager.getHabboClient(habbo);
    }

    public void showConfirmAlert(Habbo habbo, String header_text) {
        try {
            this.clientws = HabboClientManager.getHabboClient(habbo);

            JsonArray JSONArray = new JsonArray();
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("name_action",
                    RoleplayWebsocketsRoomsInfo.getTexts().getValue("confirm_" + header_text + "_evento"));
            jsonObject.addProperty("action", 1);
            jsonObject.addProperty("title_ventana",
                    RoleplayWebsocketsRoomsInfo.getTexts().getValue("confirm_" + header_text + "_title"));
            jsonObject.addProperty("desc_ventana",
                    RoleplayWebsocketsRoomsInfo.getTexts().getValue("confirm_" + header_text + "_desc"));
            jsonObject.addProperty("title_confirm",
                    RoleplayWebsocketsRoomsInfo.getTexts().getValue("confirm_" + header_text + "_confirm"));
            jsonObject.addProperty("title_cancel",
                    RoleplayWebsocketsRoomsInfo.getTexts().getValue("confirm_" + header_text + "_cancell"));
            JSONArray.add(jsonObject);

            if (clientws != null) {
                clientws.send(new MessageManager("MessageManager", true, Outcoming.SelectConfirmEvento, JSONArray));
            }
        } catch (Exception e) {

        }
    }

    public void ocultConfirmAlert(Habbo habbo) {
        try {
            this.clientws = HabboClientManager.getHabboClient(habbo);
            if (clientws != null) {
                clientws.send(new MessageManager("MessageManager", false, Outcoming.SelectConfirmEvento, null));
            }
        } catch (Exception e) {
        }
    }

    public void showSelectEventConfirm(String name, Integer id) throws SQLException {
        if (clientws != null) {
            clientws.send(new ShowVentSelectConfirm("ShowVentSelectConfirm", name, id, true));
        }
    }

    public void ocultSelectEventConfirm() throws SQLException {
        if (clientws != null) {
            clientws.send(new ShowVentSelectConfirm("ShowVentSelectConfirm", "", 0, false));
        }
    }

    public void ocultSelectEventConfirm(Habbo habbo) throws SQLException {

        this.clientws = HabboClientManager.getHabboClient(habbo);

        if (this.clientws != null) {
            clientws.send(new ShowVentSelectConfirm("ShowVentSelectConfirm", "", 0, false));
        }
    }

    public void IncomingListActions(THashMap<String, Object> data, HabboClient clientws) {
        try {

            if (clientws != null) {

                String name = ((String) data.get("name"));
                int id = (int) ((double) data.get("id"));

                new ConfirmEventsManager().ocultVentSelect(clientws.getHabbo());
                this.ocultSelectEventConfirm(clientws.getHabbo());

                if (name.equalsIgnoreCase("RealiceFunctionTestPROD")) {
                    new utilTestFunction(clientws.getHabbo());
                }

                if (name.equalsIgnoreCase("CreateNewRoomRoleplayConfirmed")) {
                    new CreateRoomGenericRoleplay().New(clientws.getHabbo());
                }

                if (name.equalsIgnoreCase("buy_herramientas")) {
                    new SelectOrdenHerramientas(name, id, clientws.getHabbo());
                }

                if (name.equalsIgnoreCase("items_ingredientes_orden_select")) {
                    new SelectOrdenItemsIngredientes(name, id, clientws.getHabbo());
                }

                if (name.equalsIgnoreCase("electrodomesticos_orden_select")) {
                    new ConfirmBuyElectrodomesticos(name, id, clientws.getHabbo());
                }

                if (name.equalsIgnoreCase("createConexionTitlesTeleport")) {
                    new EventCreateConexionTiles().setTilesHabbo(clientws.getHabbo());
                }

                if (name.equalsIgnoreCase("buy_canaspescar")) {
                    new EventConfirmBuyCanaPescar(name, id, clientws.getHabbo());
                }

                if (name.equalsIgnoreCase("select_buy_platillos_comida")) {
                    new ConfirmBuyPlatillosComidaSelect("select_buy_platillos_comida", id, clientws.getHabbo());
                }

                if (name.equalsIgnoreCase("successConfirmBuyCafe")) {
                    try {
                        HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager()
                                .getHabboRp(clientws.getHabbo());

                        if (RoomCafeteria.getEstadoEstante() == 0) {
                            // Mostrar alerta abajo
                            habboRp.showMessage("/RoleplayWebsocketsRoomsInfo/dormir/cafe.png",
                                    RoleplayWebsocketsRoomsInfo.getTexts().getValue("avise_cafe_empty"));
                            return;
                        }

                        int costo_cafe = 5;

                        if (habboRp.getDineroMonedero() <= 5) {
                            habboRp.showMessage("/RoleplayWebsocketsRoomsInfo/cash/2.png",
                                    "No tiene suficiente dinero para comprar cafe, necesitas %costo% cash.".replace(
                                            "%costo%",
                                            costo_cafe + ""));
                            return;
                        }

                        HabboClient client = HabboClientManager.getHabboClient(clientws.getHabbo());
                        try {
                            client.send(new PlaySound("PlaySound", true, "buy.mp3", client.getHabbo()));
                        } catch (SQLException e) {
                            e.printStackTrace();
                        }

                        clientws.getHabbo().givePoints(
                                RoleplayWebsocketsRoomsInfo.getConfig().getInt("seasonal.rol.primary.type"),
                                -costo_cafe);

                        habboRp.getNewInventoryHabbo().addCantidadItem("cafe", 1);
                        habboRp.getNewInventoryHabbo().updateItemsUsed();

                        RoomCafeteria.restarCafeEstante(1);

                        // Mostrar alerta abajo
                        habboRp.showMessage("/RoleplayWebsocketsRoomsInfo/dormir/cafe.png",
                                RoleplayWebsocketsRoomsInfo.getTexts().getValue("action_buyCafeSuccess")
                                        .replace("%AMOUNT%", 1 + "")
                                        .replace("%CANTIDAD%",
                                                costo_cafe + ""));

                        clientws.getHabbo()
                                .shout(RoleplayWebsocketsRoomsInfo.getTexts().getValue("action_buyCafeSuccess_msg")
                                        .replace("%AMOUNT%", 1 + "")
                                        .replace("%CANTIDAD%",
                                                costo_cafe + ""));

                        Emulator.getThreading().run(() -> {
                            try {
                                habboRp.getNewInventoryHabbo().showButtonsUsar(clientws.getHabbo());
                            } catch (Exception e) {
                            }
                        }, 500);

                    } catch (Exception e) {
                    }
                }
            }

        } catch (Exception e) {

        }
    }
}
