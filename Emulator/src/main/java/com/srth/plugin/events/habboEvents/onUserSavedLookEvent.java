package com.srth.plugin.events.habboEvents;

import java.sql.SQLException;

import com.eu.habbo.plugin.EventListener;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.plugin.EventHandler;
import com.eu.habbo.plugin.events.users.UserSavedLookEvent;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;

import gnu.trove.map.hash.THashMap;

public class onUserSavedLookEvent implements EventListener {

    private static THashMap<Short, Short> itemListX = new THashMap<Short, Short>();
    private static THashMap<Short, Short> itemListY = new THashMap<Short, Short>();

    @EventHandler
    public static void eventUserSavedLookEvent(UserSavedLookEvent event) throws SQLException {

        Habbo habbo = event.habbo;
        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (habbo == null || room == null) {
            return;
        }

        if (room.getCategory() == RoleplayWebsocketsRoomsInfo.getConfig()
                .getInt("global_room.category.roleplay.id")) {
            try {

                HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(event.habbo);

                if (habboRp.getConstructorSalas()) {
                    event.setCancelled(false);
                    return;
                }

                // // Si no estas en tu casa
                // if (!room.getName().contains("[CASA]")) {
                // event.setCancelled(true);
                // habboRp.getHabbo().whisper("Necesitas estar dentro de un ropero para cambiar
                // tu ropa.");
                // return;
                // }

                // Si no estas en tu casa
                if (habboRp.getJobActive() != null) {
                    event.setCancelled(true);
                    habboRp.getHabbo().whisper("No puedes cambiar tu ropa teniendo el uniforme de tu empleo.");
                    return;
                }

                Short evento_X = habbo.getRoomUnit().getX();
                Short evento_Y = habbo.getRoomUnit().getY();

                for (HabboItem item : room.getFloorItems()) {
                    if (item.getBaseItem().getName().equalsIgnoreCase("boutique_changing2")) {
                        setCasillasItem(item);
                    }
                }
                for (HabboItem item : room.getFloorItems()) {
                    if (item.getBaseItem().getName().equalsIgnoreCase("boutique_changing3")) {
                        setCasillasItem(item);
                    }
                }

                if (itemListX.get(evento_X) == null || itemListY.get(evento_Y) == null) {

                    event.setCancelled(true);
                    habboRp.getHabbo().whisper("Necesitas estar dentro de un ropero para cambiar tu ropa.");
                    return;
                }

            } catch (Exception e) {
            }
        }
    }

    public static void setCasillasItem(HabboItem item) {

        short newX = item.getX();
        itemListX.put(item.getX(), newX);

        short newY = item.getY();
        itemListY.put(item.getY(), newY);

    }
}
