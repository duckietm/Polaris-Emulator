package com.srth.plugin.Tutorial.partes_empleos;

import com.eu.habbo.Emulator;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Tutorial.HabboTutorial;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.websocket.Habbo.HabboClient;

import gnu.trove.map.hash.THashMap;

public class IncomingListEmpleos {

    public IncomingListEmpleos(THashMap<String, Object> data, HabboClient wsclient) {
        try {

            String get_action = ((String) data.get("action"));

            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager()
                    .getHabboRp(wsclient.getHabbo());
            HabboTutorial habboTutorial = habboRp.getHabboTutorial();

            if (get_action.equalsIgnoreCase("activeRetoEmpleoMedico")) {
                String parteProgreso = "1.1.0";
                if (habboRp.getHabboRetos().getParteProgressReto() != 0) {
                    String progresso = "1.1." + habboRp.getHabboRetos().getParteProgressReto() + "";
                    parteProgreso = progresso;
                }
                habboTutorial.setTutorialEmpleoActive(true);
                habboTutorial.setParteTutorialExtra(parteProgreso);

                habboTutorial.showTutorialEmpleo(
                        habboTutorial.getParteTutorialExtra(), true);
            }

            if (get_action.equalsIgnoreCase("activeRetoEmpleoAseo")) {

                String parteProgreso = "1.2.0";
                if (habboRp.getHabboRetos().getParteProgressReto() != 0) {
                    String progresso = "1.2." + habboRp.getHabboRetos().getParteProgressReto() + "";
                    parteProgreso = progresso;
                }

                RoleplayWebsocketsRoomsInfo.utilities().LOGGER("PARTE: " + parteProgreso);
                habboTutorial.setTutorialEmpleoActive(true);
                habboTutorial.setParteTutorialExtra(parteProgreso);

                habboTutorial.showTutorialEmpleo(
                        habboTutorial.getParteTutorialExtra(), true);
            }

            if (get_action.equalsIgnoreCase("activeRetoEmpleoPolice")) {

                String parteProgreso = "1.3.0";
                if (habboRp.getHabboRetos().getParteProgressReto() != 0) {
                    String progresso = "1.3." + habboRp.getHabboRetos().getParteProgressReto() + "";
                    parteProgreso = progresso;
                }

                RoleplayWebsocketsRoomsInfo.utilities().LOGGER("PARTE: " + parteProgreso);
                habboTutorial.setTutorialEmpleoActive(true);
                habboTutorial.setParteTutorialExtra(parteProgreso);

                habboTutorial.showTutorialEmpleo(
                        habboTutorial.getParteTutorialExtra(), true);
            }

            if (get_action.equalsIgnoreCase("activeRetoEmpleoCafetero")) {

                String parteProgreso = "1.4.0";
                if (habboRp.getHabboRetos().getParteProgressReto() != 0) {
                    String progresso = "1.4." + habboRp.getHabboRetos().getParteProgressReto() + "";
                    parteProgreso = progresso;
                }

                habboTutorial.setTutorialEmpleoActive(true);
                habboTutorial.setParteTutorialExtra(parteProgreso);

                habboTutorial.showTutorialEmpleo(
                        habboTutorial.getParteTutorialExtra(), true);
            }

            if (get_action.equalsIgnoreCase("eventRecibirPasePolicial")) {

                if (!habboTutorial.getParteTutorialExtra().equalsIgnoreCase("1.3.0")) {
                    return;
                }

                habboRp.showMessage("RoleplayWebsocketsRoomsInfo/job_polices/work_no_accion.gif?v=1",
                        RoleplayWebsocketsRoomsInfo.getTexts().getValue("job_permise_staff.need.active")
                                .replace("%br%", "\n"));
                return;

                // if (habboRp.getLevelHabbo().getLevel() <= 1) {
                // habboRp.showMessage("RoleplayWebsocketsRoomsInfo/job_polices/work_no_accion.gif?v=1",
                // "Necesitas ser nivel 2 para poder recibir este pase"
                // .replace("%br%", "\n"));
                // return;
                // }

                // if (habboRp.getBoolean("work_polices") == false) {
                // habboRp.checkSettings("work_polices", "1");
                // habboRp.updateData("work_polices", "1");
                // }

                // Emulator.getThreading().run(() -> {
                // habboRp.showMessage("RoleplayWebsocketsRoomsInfo/job_polices/police_active.png?v=1",
                // "Acabas de recibir un pase policial, ya podras acceder a la zona privada..."
                // .replace("%br%", "\n"));
                // }, 0);

                // habboTutorial.setParteTutorialExtra("1.3.1");
                // habboRp.getHabboRetos().updateProgressReto(1);

                // Emulator.getThreading().run(() -> {
                //     habboTutorial.showTutorialEmpleo(
                //             habboTutorial.getParteTutorialExtra(), true);
                // }, 5000);

            }

            if (get_action.equalsIgnoreCase("finaliceTutorialMedico")) {
                new EventosEmpleosTutorial(wsclient.getHabbo()).finaliceTutorialMedico();
            }

        } catch (Exception e) {
        }
    }

}
