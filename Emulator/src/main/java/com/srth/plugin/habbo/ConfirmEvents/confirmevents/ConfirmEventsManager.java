package com.srth.plugin.habbo.ConfirmEvents.confirmevents;

import java.sql.SQLException;

import com.eu.habbo.habbohotel.users.Habbo;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.Delitos.PoliceTransportManager;
import com.srth.plugin.habbo.ConfirmEvents.confirmselectevents.ConfirmSelectEventsManager;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.Habbo.HabboClientManager;
import com.srth.plugin.websocket.communication.outgoing.messages.ShowConfirmAlert;
import com.srth.plugin.websocket.communication.outgoing.messages.ShowVentSelect;

import gnu.trove.map.hash.THashMap;

public class ConfirmEventsManager {

    HabboClient clientws;

    public ConfirmEventsManager() {

    }

    public void setHabbo(Habbo habbo) {
        this.clientws = HabboClientManager.getHabboClient(habbo);
    }

    public void showVentSelect(String name, Integer action, Boolean costo_active, Integer cash_actual,
            String url_images, Boolean show) throws SQLException {
        if (clientws != null) {
            clientws.send(
                    new ShowVentSelect("ShowVentSelect", name, action, costo_active, cash_actual, url_images, true));
        }
    }

    public void ocultVentSelect() throws SQLException {
        if (clientws != null) {
            clientws.send(new ShowVentSelect("ShowVentSelect", "name", 0, false, 0, "", false));
        }
    }

    public void ocultVentSelect(Habbo habbo) throws SQLException {
        this.clientws = HabboClientManager.getHabboClient(habbo);
        if (this.clientws != null) {
            clientws.send(new ShowVentSelect("ShowVentSelect", "name", 0, false, 0, "", false));
        }
    }

    public void IncomingListActions(THashMap<String, Object> data, HabboClient clientws) {
        try {
            if (clientws != null) {

                RoleplayWebsocketsRoomsInfo.utilities().LOGGER(data + "");

                new ConfirmSelectEventsManager().ocultSelectEventConfirm(clientws.getHabbo());

                String name = (String) data.get("name");
                int action = (int) ((double) data.get("action"));

                if (name.equalsIgnoreCase("comer_platillo")) {
                    if (action == 1) {
                        clientws.send(new ShowConfirmAlert("ShowConfirmAlert", "comer_platillo", 1, 0, false, ""));
                        new EventConfirmFood(clientws.getHabbo());
                    }
                }

                if (name.equalsIgnoreCase("buy_foods_machine")) {
                    if (action == 1) {
                        clientws.send(new ShowConfirmAlert("ShowConfirmAlert", "buy_foods_machine", 1, 0, false, ""));

                        new EventConfirmBuyFoodsMachine(clientws.getHabbo());
                    }
                }

                if (name.equalsIgnoreCase("bote_basura")) {
                    if (action == 1) {
                        clientws.send(new ShowConfirmAlert("ShowConfirmAlert", "bote_basura", 1, 0, false, ""));

                        new EventConfirmBoteBasura(clientws.getHabbo());
                    }
                }

                if (name.equalsIgnoreCase("GotoHospital")) {
                    if (action == 1) {
                        clientws.send(new ShowConfirmAlert("ShowConfirmAlert", "GotoHospital", 1, 0, false, ""));
                        clientws.getHabbo()
                                .goToRoom(RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.job_medico.id"));
                    }
                }

                if (name.equalsIgnoreCase("bote_basura_empty")) {
                    if (action == 1) {
                        clientws.send(new ShowConfirmAlert("ShowConfirmAlert", "bote_basura_empty", 1, 0, false, ""));

                        new EventConfirmBoteBasuraEmpty(clientws.getHabbo());
                    }
                }

                if (name.equalsIgnoreCase("aseo_contenedor")) {
                    if (action == 1) {
                        clientws.send(new ShowConfirmAlert("ShowConfirmAlert", "aseo_contenedor", 1, 0, false, ""));

                        new EventConfirmAseoContenedor(clientws.getHabbo());
                    }
                }

                if (name.equalsIgnoreCase("exit_recorrido")) {
                    if (action == 1) {
                        clientws.send(new ShowConfirmAlert("ShowConfirmAlert", "exit_recorrido", 1, 0, false, ""));

                        PoliceTransportManager.cancelForPolice(clientws.getHabbo());
                        RoleplayWebsocketsRoomsInfo.getControllerRecorridosTaxi()
                                .eventFinaliceRecorrido(clientws.getHabbo());

                    }
                }
                if (name.equalsIgnoreCase("cancel_exit_recorrido")) {
                    if (action == 1) {
                        RoleplayWebsocketsRoomsInfo.getControllerRecorridosTaxi()
                                .removeCacheRecorrido(clientws.getHabbo());
                    }
                    if (action == 0) {
                        RoleplayWebsocketsRoomsInfo.getControllerRecorridosTaxi()
                                .removeCacheRecorrido(clientws.getHabbo());
                    }
                }
            }
        } catch (Exception e) {
        }
    }

}
