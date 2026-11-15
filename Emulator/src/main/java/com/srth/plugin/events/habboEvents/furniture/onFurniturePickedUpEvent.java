package com.srth.plugin.events.habboEvents.furniture;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.plugin.EventHandler;
import com.eu.habbo.plugin.EventListener;
import com.eu.habbo.plugin.events.furniture.FurniturePickedUpEvent;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;

public class onFurniturePickedUpEvent implements EventListener {

    // 1. Definimos la lista de nombres de furnis bloqueados
    private static final List<String> BLOQUED_FURNI_NAMES = Arrays.asList(
            "habbox_pabllo_barriere2", "val14_door", "cpunk_c15_stage1", "urban_lamp", "cpunk_c15_road",
            "room_switcher_home", "roombg_color","ads_background");

    @EventHandler
    public static void FurniturePickedUpEvent(FurniturePickedUpEvent event) throws SQLException {
        // Simplificamos la comprobación inicial
        if (event.habbo == null) {
            return;
        }

        final Habbo habbo = event.habbo;
        final Room room = habbo.getHabboInfo().getCurrentRoom();

        if (room == null)
            return;

        // Comprobamos la categoría de la sala
        if (room.getCategory() == RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.category.roleplay.id")) {

            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

            if (room.getId() == habboRp.getInt("global.home.room.rp.id")) {

                // 2. Obtenemos el nombre del item actual
                String furniName = event.furniture.getBaseItem().getName();

                boolean isBlocked = BLOQUED_FURNI_NAMES.stream()
                        .anyMatch(name -> name.equalsIgnoreCase(furniName));

                if (isBlocked) {
                    event.setCancelled(true);

                    Emulator.getThreading().run(() -> {
                        habboRp.showMessage("RoleplayWebsocketsRoomsInfo/job_polices/work_no_accion.gif?v=1",
                                "No puedes modificar el estado o posición de este furni...");
                    }, 0);
                }
            }
        }

    }
}
