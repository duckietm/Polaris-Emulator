package com.srth.plugin.events.habboEvents.furniture;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.habboway.nux.NuxAlertComposer;
import com.eu.habbo.plugin.EventHandler;
import com.eu.habbo.plugin.EventListener;
import com.eu.habbo.plugin.events.furniture.FurniturePlacedEvent;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;

public class onFurniturePlacedEvent implements EventListener {

    @EventHandler
    public static void FurniturePlacedEvent(FurniturePlacedEvent event) throws SQLException {

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

                if (furniName.equalsIgnoreCase("rp_food_ktchn")) {

                    if (habboRp.getCacheInt("platillo_colocado") == 1) {

                        event.setCancelled(true);
                        habboRp.showMessage("RoleplayWebsocketsRoomsInfo/tutorial/platillos.png?v=1",
                                "¡Debes colocar solo 1 platillo, el resto dejalos en tu inventario!...");

                        habbo.getClient().sendResponse(new NuxAlertComposer("inventory/hide"));
                    }

                    habboRp.registerCache("platillo_colocado", "1");
                    habbo.getClient().sendResponse(new NuxAlertComposer("inventory/hide"));
                }
            }
        }
    }
}