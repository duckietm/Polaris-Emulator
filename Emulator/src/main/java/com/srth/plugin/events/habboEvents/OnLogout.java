package com.srth.plugin.events.habboEvents;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.plugin.EventHandler;
import com.eu.habbo.plugin.EventListener;
import com.eu.habbo.plugin.events.users.UserDisconnectEvent;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Rooms.Mapa.Taxi.TaxiBotRideManager;
import com.srth.plugin.habbo.Delitos.PoliceTransportManager;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.Jobs.Job;
import com.srth.plugin.habbo.Jobs.Player.LevelPlayer;
import com.srth.plugin.habbo.Jobs.WorkSystemLevelManager;
import com.srth.plugin.websocket.Habbo.HabboClient;
import java.sql.SQLException;

public class OnLogout implements EventListener {

    @EventHandler
    public static void eventOnLogout(UserDisconnectEvent event) throws SQLException {

        Habbo habbo = event.habbo;

        if (habbo == null) {
            return;
        }

        PoliceTransportManager.handleLogout(event);
        TaxiBotRideManager.handleLogout(habbo);

        if (habbo != null)

            // vaciar caches
            habbo.getHabboStats().cache.clear();

        // Si estaba en una sala RP se establece esa ultima sala de entrada
        Room roomvieja = habbo.getHabboInfo().getCurrentRoom();
        if (roomvieja != null
                && roomvieja.getCategory()
                        == RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.category.roleplay.id")) {
            int id_room_vieja = roomvieja.getId();
            habbo.getHabboInfo().setHomeRoom(id_room_vieja);
        } else {
            int id_room_vieja = Emulator.getConfig().getInt("hotel.home.room");
            habbo.getHabboInfo().setHomeRoom(id_room_vieja);
        }

        // Resetear trabajos activos
        HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(event.habbo);

        // Recordar posicion en la sala
        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room != null) {
            RoomTile roomtile = habbo.getRoomUnit().getCurrentLocation();
            RoleplayWebsocketsRoomsInfo.utilities().LOGGER(" -- ESTADO DEL ROOMTILE: " + roomtile.state);
            if (roomtile.state != RoomTileState.INVALID) {
                RoleplayWebsocketsRoomsInfo.utilities()
                        .LOGGER(" -- ROOMTILE: X:" + roomtile.x + " Y:" + roomtile.y + "Z: " + roomtile.z);
                String dataRoomTileSave = "x:" + roomtile.x + ";y:" + roomtile.y + ";z:" + roomtile.z;
                habboRp.checkSettings("dataRoomTileSave", dataRoomTileSave);
                habboRp.updateData("dataRoomTileSave", dataRoomTileSave);

                // Si estaba en una sala RP se establece esa ultima sala de entrada
                habbo.getHabboInfo().setHomeRoom(room.getId());
            }
        }

        Job jobActive = habboRp.getJobActive();
        if (jobActive != null) {

            RoleplayWebsocketsRoomsInfo.utilities().LOGGER("LOGOUT: " + jobActive.getName());

            habboRp.update("ultimo_job_active", jobActive.getName());
            habboRp.updateData("ultimo_job_active", jobActive.getName() + "");
            habboRp.checkSettings("ultimo_job_active", jobActive.getName() + "");

            RoleplayWebsocketsRoomsInfo.utilities().LOGGER("LOGOUT SAVE: " + habboRp.getValue("ultimo_job_active"));

            jobActive.removeEmpleado(habbo);
        }

        LevelPlayer player = WorkSystemLevelManager.getOnlinePlayers()
                .get(habbo.getHabboInfo().getId());
        if (player != null) {
            player.resetTasksActive();
            WorkSystemLevelManager.removeOnlinePlayer(player.getPlayerStats().getUserId());
        }

        HabboClient client = habboRp.getHabboClient();
        if (client != null) {
            // HabboClientManager.killClient(client.getChannel().id());
            // SSOClientManager.SSOclients.remove(event.habbo.getHabboInfo().getSso());
        }
    }
}
