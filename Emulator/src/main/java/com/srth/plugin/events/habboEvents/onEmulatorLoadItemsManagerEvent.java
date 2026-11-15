package com.srth.plugin.events.habboEvents;

import java.sql.SQLException;

import com.eu.habbo.plugin.EventHandler;
import com.eu.habbo.plugin.EventListener;
import com.eu.habbo.plugin.events.emulator.EmulatorLoadItemsManagerEvent;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.interactions.hackercajeros.control.InteractionManagerHackerCajeros;
import com.srth.plugin.interactions.vidayhambre.control.InteractionManagerVidayHambre;

public class onEmulatorLoadItemsManagerEvent implements EventListener {

    @EventHandler
    public static void onLogin(EmulatorLoadItemsManagerEvent event) throws SQLException {

        // Registrar interacciones
        new InteractionManagerVidayHambre().load();
        new InteractionManagerHackerCajeros().load();

        String ANSI_GREEN = "\u001B[32m";
        RoleplayWebsocketsRoomsInfo.utilities()
                .LOGGER(ANSI_GREEN + "----------------------------------------------------------------------");
        RoleplayWebsocketsRoomsInfo.utilities()
                .LOGGER(ANSI_GREEN + "[ROLEPLAY WEBSOCKETS ROOM INFO] | CARGADAS LAS INTERACCIONES ROLEPLAY");
        RoleplayWebsocketsRoomsInfo.utilities()
                .LOGGER(ANSI_GREEN + "----------------------------------------------------------------------");

    }

}
