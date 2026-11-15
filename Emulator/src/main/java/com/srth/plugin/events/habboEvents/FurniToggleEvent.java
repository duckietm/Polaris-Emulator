package com.srth.plugin.events.habboEvents;

import java.sql.SQLException;

import com.eu.habbo.plugin.EventHandler;
import com.eu.habbo.plugin.EventListener;
import com.eu.habbo.plugin.events.furniture.FurnitureToggleEvent;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.Jobs.WorkSystemLevelManager;
import com.srth.plugin.habbo.Shooter.ShooterManager;
import com.srth.plugin.websocket.Habbo.HabboClient;

public class FurniToggleEvent implements EventListener {

    @EventHandler
    public static void eventFurniToggleEvent(FurnitureToggleEvent event) throws SQLException {

        HabboClient client = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(event.habbo).getHabboClient();

        if (client == null) {
            return;
        }

        RoleplayWebsocketsRoomsInfo.getRoomsRoleplayManager().eventUserFurniDoubleClick(event);

        // Comprobar si se hace un click en estos items
        RoleplayWebsocketsRoomsInfo.getItemActionManager().getReceibedToggleEvent(event);

        // Comprobar si se hace doble click para las tareas
        new WorkSystemLevelManager().getReceibedToggleEvent(event);

        RoleplayWebsocketsRoomsInfo.getRoomsRoleplayManager().getReceibedToggleEvent(event);

        ShooterManager.getReceibedToggleEvent(event);
    }
}
