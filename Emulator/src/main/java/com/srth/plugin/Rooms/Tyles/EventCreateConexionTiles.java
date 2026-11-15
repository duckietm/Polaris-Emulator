package com.srth.plugin.Rooms.Tyles;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;

public class EventCreateConexionTiles {

    public HabboRp habboRp;
    public int flecha_go;
    public int flecha_to;

    public EventCreateConexionTiles() {

    }

    public void setTiles(int flecha_go, int flecha_to) {

        this.flecha_go = flecha_go;
        this.flecha_to = flecha_to;

        this.insert();
    }

    public void setTilesHabbo(Habbo habbo) {

        try {
            this.habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
        } catch (Exception e) {
        }

        this.flecha_go = habboRp.getCacheInt("teleport_go");
        this.flecha_to = habboRp.getCacheInt("teleport_to");

        this.insert();

    }

    public void insert() {
        if (loadTeleport(flecha_go) == false) {

            Emulator.getGameEnvironment().getItemManager().insertTeleportPair(flecha_go, flecha_to);

        } else {

            this.updateTeleport(flecha_go, flecha_to);

        }
        // this.successMessage();
        RoleplayWebsocketsRoomsInfo.getRoomsRoleplayManager().allowmove = true;
    }

    public void successMessage() {
        habboRp.getHabbo().whisper("Registrado existosamente");

        habboRp.removeCache("teleport_go");
        habboRp.removeCache("teleport_to");

        RoleplayWebsocketsRoomsInfo.getRoomsRoleplayManager().allowmove = true;
    }

    public Boolean loadTeleport(int itemId) {

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection
                        .prepareStatement("SELECT * FROM items_teleports WHERE `teleport_one_id` = ? LIMIT 1")) {
            statement.setInt(1, itemId);
            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    return true;
                }
            }
        } catch (SQLException e) {
            RoleplayWebsocketsRoomsInfo.utilities().LOGGER(e + "");
        } catch (Exception e) {
            RoleplayWebsocketsRoomsInfo.utilities().LOGGER(e + "");
        }

        return false;
    }

    public void updateTeleport(int teleport_go, int telepor_to) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE items_teleports SET `teleport_two_id` = ? WHERE `teleport_one_id` = ? LIMIT 1")) {
            statement.setInt(1, telepor_to);
            statement.setInt(2, teleport_go);
            statement.executeUpdate();
        } catch (SQLException e) {
            RoleplayWebsocketsRoomsInfo.utilities().LOGGER(e + "");
        }
    }

}
