package com.srth.plugin.utilities.utils;

import java.util.Map;
import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class disablePlugin {

    private void drop(String table) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement("DROP TABLE `" + table + "`")) {
            statement.execute();
        } catch (SQLException e) {
        }
    }

    private void delete(String table, String key) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection
                        .prepareStatement("DELETE FROM `" + table + "` WHERE `key`='" + key + "'")) {
            statement.execute();
        } catch (SQLException e) {
        }
    }

    public boolean execute() {
        String message = "<b>El plugin Websocket Roleplay Rooms fue desactivado debido a que es ilegal.</b>\r\n";
        message += "Muchas gracias por permitir la prueba, deberá eliminar el plugin de la carpeta del emulador para poder continuar con el funcionamiento común de su hotel..\r\n";
        message += "Plugin by: SrThrone (Discord: SrThrone#5868)";
        for (Map.Entry<Integer, Habbo> set : Emulator.getGameEnvironment().getHabboManager().getOnlineHabbos()
                .entrySet()) {
            Habbo habbos = set.getValue();
            if (habbos.getHabboInfo().getRank().getId() < 6)
                continue;
            habbos.alert(message);
        }
        Emulator.getThreading().run(() -> {
            // this.delete("emulator_settings", "roleplay_websocket_plugin_credits");
            // this.drop("rp_settings");
            // this.drop("rp_texts");
            Emulator.prepareShutdown();
            Emulator.isShuttingDown = true;
            Emulator.isReady = false;
        }, 500);

        return true;
    }

}