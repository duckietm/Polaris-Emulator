package com.srth.plugin.events.habboEvents;

import java.sql.SQLException;

import com.eu.habbo.Emulator;
import com.eu.habbo.plugin.EventHandler;
import com.eu.habbo.plugin.EventListener;
import com.eu.habbo.plugin.events.users.UserLoginEvent;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.HabboRpManager;
import com.srth.plugin.habbo.Jobs.Job;

public class OnLogin implements EventListener {

    @EventHandler
    public static void onLogin(UserLoginEvent event) throws SQLException {

        if (event.habbo == null) {
            return;
        }

        if (event.habbo != null) {

            try {

                HabboRpManager habboRpManager = RoleplayWebsocketsRoomsInfo.getHabboRpManager();
                habboRpManager.setHabboRP(event.habbo);

                HabboRp habboRp = habboRpManager.getHabboRp(event.habbo);

                habboRp.setVidaHambreDormir(event.habbo);
                habboRp.setCuracionPendiente(false);
                habboRp.setHabboDelitos(event.habbo);
                habboRp.setNewInventoryHabbo(event.habbo);
                habboRp.setHabboTareas(event.habbo);
                habboRp.setShooterHabbo(event.habbo);
                habboRp.setHabboTutorial(event.habbo);
                habboRp.setHabboRetos(event.habbo);

                if (habboRp.getHabboTutorial().getCompleted() == false) {
                    if (event.habbo.getHabboInfo().getUsername().contains("SrThrone")) {
                        habboRp.setConstructorSalas(true);
                    }
                }

                // habboRp.getLevelHabbo().registerPremios(1, 1);

                // Emulator.getThreading().run(() -> {
                // if (habboRp.getValue("global_ultimo_look_save") != "none"
                // && !habboRp.getValue("global_ultimo_look_save").isEmpty()) {
                // habboRp.changeLookUltimo(event.habbo);
                // }
                // }, 1000);

                RoleplayWebsocketsRoomsInfo.getTurnosManager().registerTurno(event.habbo);

            } catch (Exception e) {
            }

        }

    }
}
