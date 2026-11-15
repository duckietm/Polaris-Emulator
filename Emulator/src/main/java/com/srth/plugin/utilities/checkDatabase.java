package com.srth.plugin.utilities;

import com.eu.habbo.Emulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class checkDatabase {
    private static final Logger LOGGER = LoggerFactory.getLogger(checkDatabase.class);

    public static void ICheckDatabase() throws Exception {
        try {
            boolean reloadPermissions = true;

            // Permissions vida y hambre
            reloadPermissions = registerPermission("cmd_curar", 1, "Permiso para curar", 1, reloadPermissions);
            reloadPermissions = registerPermission("cmd_comer", 1, "Permiso para comer", 1, reloadPermissions);

            // Permissions hacker de cajeros
            reloadPermissions = registerPermission("cmd_arrestar", 1, "Permiso para arrestar", 1, reloadPermissions);
            reloadPermissions = registerPermission("cmd_expediente", 1, "Permiso para ver expediente", 1,
                    reloadPermissions);
            reloadPermissions = registerPermission("cmd_give_police", 1, "Permiso para dar placa o rol policial", 1,
                    reloadPermissions);
            reloadPermissions = registerPermission("cmd_robar", 1, "Permiso para robar", 1, reloadPermissions);

            reloadPermissions = registerPermission("cmd_update_rp_config", 1,
                    "Permiso para actualizar configuracion RP", 0, reloadPermissions);
            reloadPermissions = registerPermission("cmd_update_rp_text", 1, "Permiso para actualizar textos RP", 0,
                    reloadPermissions);

            if (reloadPermissions)
                Emulator.getGameEnvironment().getPermissionsManager().reload();

        } catch (Exception e) {
            LOGGER.error(" [WEBSOCKETS ROOMS INFO] Failed to check database for permissions", e);
        }
    }

    private static boolean registerPermission(String permissionKey, int maxValue, String comment, int defaultRank,
            boolean defaultReturn) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO `permission_definitions` (`permission_key`, `max_value`, `comment`, `rank_1`, `rank_2`, `rank_3`, `rank_4`, `rank_5`, `rank_6`, `rank_7`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE `permission_key` = `permission_key`")) {
            statement.setString(1, permissionKey);
            statement.setInt(2, maxValue);
            statement.setString(3, comment);
            statement.setInt(4, defaultRank);
            statement.setInt(5, defaultRank);
            statement.setInt(6, defaultRank);
            statement.setInt(7, defaultRank);
            statement.setInt(8, defaultRank);
            statement.setInt(9, defaultRank);
            statement.setInt(10, defaultRank);
            statement.execute();
            return true;
        } catch (SQLException e) {
            LOGGER.error("Failed to register permission: " + permissionKey, e);
        }

        return defaultReturn;
    }

}