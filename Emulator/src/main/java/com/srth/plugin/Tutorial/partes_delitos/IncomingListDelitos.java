package com.srth.plugin.Tutorial.partes_delitos;

import com.eu.habbo.Emulator;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Tutorial.HabboTutorial;
import com.srth.plugin.Tutorial.partes_empleos.EventosEmpleosTutorial;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.Inventorys.InventoryHabboNew;
import com.srth.plugin.websocket.Habbo.HabboClient;

import gnu.trove.map.hash.THashMap;

public class IncomingListDelitos {

    public IncomingListDelitos(THashMap<String, Object> data, HabboClient wsclient) {

        try {

            String get_action = ((String) data.get("action"));

            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager()
                    .getHabboRp(wsclient.getHabbo());
            HabboTutorial habboTutorial = habboRp.getHabboTutorial();

            if (get_action.equalsIgnoreCase("activeEventIniciacionPvpGolpes")) {

                String parteProgreso = "2.1.0";
                if (habboRp.getHabboRetos().getParteProgressReto() != 0) {
                    String progresso = "2.1." + habboRp.getHabboRetos().getParteProgressReto() + "";
                    parteProgreso = progresso;
                }

                habboTutorial.setTutorialEmpleoActive(true);
                habboTutorial.setParteTutorialExtra(parteProgreso);

                habboTutorial.showTutorialDelitos(
                        habboTutorial.getParteTutorialExtra(), true);

                InventoryHabboNew inventoryHabboNew = habboRp.getNewInventoryHabbo();

                if (inventoryHabboNew.getItem("llave") == null) {
                    inventoryHabboNew.createItem("llave", 1);
                    Emulator.getThreading().run(() -> {
                        habboRp.showMessage("RoleplayWebsocketsRoomsInfo/hack_cajeros/llave.png?v=1",
                                "Acabas de recibir tu arma llave, equipala y usala para poder golpear a alguién más..."
                                        .replace("%br%", "\n"));
                    }, 500);
                }

            }

            if (get_action.equalsIgnoreCase("activeEventIniciacionPvpArmas")) {

                String parteProgreso = "2.2.0";
                if (habboRp.getHabboRetos().getParteProgressReto() != 0) {
                    String progresso = "2.2." + habboRp.getHabboRetos().getParteProgressReto() + "";
                    parteProgreso = progresso;
                }

                habboTutorial.setTutorialEmpleoActive(true);
                habboTutorial.setParteTutorialExtra(parteProgreso);

                habboTutorial.showTutorialDelitos(
                        habboTutorial.getParteTutorialExtra(), true);
            }

            if (get_action.equalsIgnoreCase("activeEventCajeroAutomaticoLlave")) {

                String parteProgreso = "2.3.0";
                if (habboRp.getHabboRetos().getParteProgressReto() != 0) {
                    String progresso = "2.3." + habboRp.getHabboRetos().getParteProgressReto() + "";
                    parteProgreso = progresso;
                }

                habboTutorial.setTutorialEmpleoActive(true);
                habboTutorial.setParteTutorialExtra(parteProgreso);

                habboTutorial.showTutorialDelitos(
                        habboTutorial.getParteTutorialExtra(), true);
            }

            if (get_action.equalsIgnoreCase("eventRecibirArmaIlegal")) {
                new EventosDelitosTutorial().eventRecibirArmaIlegal(wsclient.getHabbo());
            }
            if (get_action.equalsIgnoreCase("eventRecibirLlaveCajeroAutomatico")) {
                new EventosDelitosTutorial().eventRecibirLlaveCajeroAutomatico(wsclient.getHabbo());
            }

        } catch (Exception e) {
        }

    }

}
