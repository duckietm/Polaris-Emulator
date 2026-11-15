package com.srth.plugin.Rooms.Mapa.Actions;

import java.sql.SQLException;
import com.eu.habbo.habbohotel.users.Habbo;
import com.srth.plugin.Rooms.Mapa.MapaManager;

public class SelectTypeRooms {

    public SelectTypeRooms(Habbo habbo, int type) throws SQLException {

        habbo.getHabboStats().cache.put("type_rooms_mapa_cache", type);
        new MapaManager().viewMapa(habbo, type);

    }
}
