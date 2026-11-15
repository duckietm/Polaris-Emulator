package com.srth.plugin.events.habboEvents;

import java.sql.SQLException;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.plugin.EventHandler;
import com.eu.habbo.plugin.EventListener;
import com.eu.habbo.plugin.events.users.UserExecuteCommandEvent;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;

public class onUserExecuteCommand implements EventListener {

    @EventHandler
    public static void eventUserExecuteCommand(UserExecuteCommandEvent event) throws SQLException {
        Habbo habbo = event.habbo;
        if (habbo != null) {

            // RoleplayWebsocketsRoomsInfo.utilities().LOGGER(event.command.permission);

            String permission = event.command.permission;
            Room room = habbo.getHabboInfo().getCurrentRoom();

            if (room == null) {
                return;
            }

            // Comprobamos la categoría de la sala
            if (room.getCategory() == RoleplayWebsocketsRoomsInfo.getConfig()
                    .getInt("global_room.category.roleplay.id")) {

                HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

                switch (permission) {
                    case "cmd_maxfloor":
                        event.setCancelled(true);
                        Emulator.getThreading().run(() -> {
                            habboRp.showMessage("RoleplayWebsocketsRoomsInfo/job_polices/work_no_accion.gif?v=1",
                                    "No puedes modificar utilizar este comando en salas roleplay...");
                        }, 0);
                        break;
                    case "cmd_autofloor":
                        event.setCancelled(true);
                        Emulator.getThreading().run(() -> {
                            habboRp.showMessage("RoleplayWebsocketsRoomsInfo/job_polices/work_no_accion.gif?v=1",
                                    "No puedes modificar utilizar este comando en salas roleplay...");
                        }, 0);
                        break;
                    default:
                        break;
                }
            }

        }
    }
}
