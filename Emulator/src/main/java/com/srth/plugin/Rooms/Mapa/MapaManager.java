package com.srth.plugin.Rooms.Mapa;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Rooms.Mapa.Actions.GoToRoomTaxi;
import com.srth.plugin.Rooms.Mapa.Actions.SelectTypeRooms;
import com.srth.plugin.Rooms.Mapa.Taxi.ControllerRecorridosTaxi;
import com.srth.plugin.Rooms.Mapa.Taxi.RecorridoTaxi;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.ConfirmEvents.confirmselectevents.ConfirmSelectEventsManager;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.Habbo.HabboClientManager;
import com.srth.plugin.websocket.communication.outgoing.messages.ShowMapaRoleplay;
import gnu.trove.map.hash.THashMap;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class MapaManager {

    private final int[] types = { 0, 1, 2, 3, 4, 5 };
    private final String[] type_titles = { "Populares", "Todas las salas", "Empleos", "Normales", "Habitaciones", "+" };
    private final String[] type_names;
    private final String[] type_descs;

    private final THashMap<Integer, Room> listRooms = new THashMap<>();

    private final String url_images;
    private final String global_url_images;
    private final String look_base;
    private final String url_photos_base;
    private final String url_hotel_base;

    public MapaManager() {
        this.url_hotel_base = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.url.hotel.base");
        this.global_url_images = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url_images")
                .replace("%url.hotel.base%", url_hotel_base);
        this.url_images = global_url_images + "/";

        this.look_base = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.look.ruta")
                + "&headonly=1&size=a&gesture=sml&direction=2&head_direction=1";
        this.url_photos_base = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url.hotel.photos")
                .replace("%url.hotel.base%", url_hotel_base) + "thumbnail/";

        this.type_names = new String[] {
                getText("window_mapa.type0_name"), getText("window_mapa.type1_name"),
                getText("window_mapa.type2_name"), getText("window_mapa.type3_name"),
                getText("window_mapa.type4_name"), getText("window_mapa.type4_name")
        };
        this.type_descs = new String[] {
                getText("window_mapa.type0_desc"), getText("window_mapa.type1_desc"),
                getText("window_mapa.type2_desc"), getText("window_mapa.type3_desc"),
                getText("window_mapa.type4_desc"), getText("window_mapa.type4_name")
        };
    }

    private String getText(String key) {
        return RoleplayWebsocketsRoomsInfo.getTexts().getValue(key);
    }

    public void setMapaRoom(String name, int room_id) {
        String type = "normales";
        RoleplayWebsocketsRoomsInfo.getConfig().checkSettings("global_room_" + type + "." + name, room_id + "");
        RoleplayWebsocketsRoomsInfo.getConfig().updateData("global_room_" + type + "." + name, room_id + "");
    }

    public void viewMapa(Habbo habbo, int type) throws SQLException {
        this.ListMapa(habbo, type);
    }

    public THashMap<Integer, Room> getListRooms() {
        return listRooms;
    }

    public void loadAll() {
        String query = "SELECT value FROM rp_settings WHERE `key` LIKE '%global_room.job%' OR `key` LIKE '%global_room_normales%' ORDER BY `value` DESC LIMIT 16";
        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query);
                ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                Room room = Emulator.getGameEnvironment().getRoomManager().loadRoom(rs.getInt("value"));
                if (room != null) {
                    listRooms.put(room.getId(), room);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void IncomingListActions(THashMap<String, Object> data, HabboClient wsclient) {
        if (wsclient == null)
            return;
        try {
            String name = (String) data.get("name");
            int action = data.containsKey("action") ? (int) ((double) data.get("action")) : 0;

            switch (name) {
                case "ShowMapaRoleplay":
                    int cachedType = wsclient.getHabbo().getHabboStats().cache.containsKey("type_rooms_mapa_cache")
                            ? (int) wsclient.getHabbo().getHabboStats().cache.get("type_rooms_mapa_cache")
                            : 0;
                    this.viewMapa(wsclient.getHabbo(), cachedType);
                    break;

                case "SelectMapaRoleplay":
                    new SelectTypeRooms(wsclient.getHabbo(), action);
                    break;

                case "CreateRoomRoleplay":
                    wsclient.send(
                            new ShowMapaRoleplay("ShowMapaRoleplay", false, null,
                                    null));
                    wsclient.getHabbo().whisper("No disponible temporalmente.");

                    // new ConfirmSelectEventsManager().showConfirmAlert(wsclient.getHabbo(),
                    // "createRoomRoleplay");
                    break;
                case "GoToRoomTaxi":
                    handleTaxi(wsclient, action);
                    break;

                case "GoToRoomEvent":
                    handleEventTeleport(wsclient, action);
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleTaxi(HabboClient wsclient, int roomId) {
        try {
            if (wsclient.getHabbo().getHabboInfo().getCurrentRoom().getId() != roomId) {

                HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(wsclient.getHabbo());
                int homeRoomUser = habboRp.getInt("global.home.room.rp.id");
                Room roomActual = wsclient.getHabbo().getHabboInfo().getCurrentRoom();

                ControllerRecorridosTaxi controllerRecorridosTaxi = RoleplayWebsocketsRoomsInfo
                        .getControllerRecorridosTaxi();
                RecorridoTaxi recorrido = controllerRecorridosTaxi
                        .getRecorridoTaxi(wsclient.getHabbo());
                if (recorrido == null) {
                    controllerRecorridosTaxi.setRecorridoTaxi(wsclient.getHabbo(),
                            roomId);
                }

                wsclient.send(
                        new ShowMapaRoleplay("ShowMapaRoleplay", false, null, null));

                // GoToRoomTaxi taxi = new GoToRoomTaxi();
                // taxi.setDefaultSumatoria(5);
                // taxi.goTo(wsclient.getHabbo(), roomId);

            } else {
                RoleplayWebsocketsRoomsInfo.utilities().showBubbleAlert(
                        "RoleplayWebsocketsRoomsInfo/tutorial/casa.png",
                        "global_alert.room.actual",
                        wsclient.getHabbo().getClient());
            }
        } catch (Exception e) {
        }
    }

    private void handleEventTeleport(HabboClient wsclient, int roomId) {
        Room room = Emulator.getGameEnvironment().getRoomManager().loadRoom(roomId);
        if (room != null) {
            String msg = getText("window_mapa.msg_visit_exitosa").replace("%room_name%", room.getName());
            wsclient.getHabbo().talk(msg, RoomChatMessageBubbles.NORMAL);
            wsclient.getHabbo().goToRoom(roomId);
        }
    }

    public void ListMapa(Habbo habbo, int type) throws SQLException {
        HabboClient client = HabboClientManager.getHabboClient(habbo);
        JsonArray mapsArray = new JsonArray();
        int randomVer = Emulator.getRandom().nextInt(20);
        int currentRoomId = (habbo.getHabboInfo().getCurrentRoom() != null)
                ? habbo.getHabboInfo().getCurrentRoom().getId()
                : 0;

        if (type == 0) { // POPULARES
            List<Room> rooms = Emulator.getGameEnvironment().getRoomManager()
                    .getPopularRooms(Emulator.getConfig().getInt("hotel.navigator.popular.amount"));
            int i = 0;
            for (Room room : rooms) {
                if (room.getCategory() != RoleplayWebsocketsRoomsInfo.getConfig()
                        .getInt("global_room.category.roleplay.id"))
                    continue;
                mapsArray.add(buildRoomJson(room, i++, currentRoomId, type, randomVer));
            }
        } else { // SQL BASED TYPES (Todas, Empleos, Normales, Habitaciones)
            String query;
            String param;

            if (type == 4) {
                query = "SELECT value FROM rp_users_settings WHERE `key` = ? ORDER BY `value` DESC LIMIT 12";
                param = "global.home.room.rp.id";
            } else if (type == 1) {
                query = "SELECT value FROM rp_settings WHERE `key` LIKE '%global_room.job%' OR `key` LIKE '%global_room_normales%' ORDER BY `value` DESC LIMIT 16";
                param = null; // No necesita parámetro específico
            } else {
                query = "SELECT value FROM rp_settings WHERE `key` LIKE ? ORDER BY `value` DESC LIMIT 16";
                param = (type == 2) ? "%global_room.job%" : "%global_room_normales%";
            }

            try (Connection conn = Emulator.getDatabase().getDataSource().getConnection();
                    PreparedStatement pstmt = conn.prepareStatement(query)) {
                if (param != null)
                    pstmt.setString(1, param);

                try (ResultSet rs = pstmt.executeQuery()) {
                    int i = 0;
                    while (rs.next()) {
                        Room room = Emulator.getGameEnvironment().getRoomManager().loadRoom(rs.getInt("value"));
                        if (room != null)
                            mapsArray.add(buildRoomJson(room, i++, currentRoomId, type, randomVer));
                    }
                }
            }
        }

        boolean hasData = mapsArray.size() > 0;
        client.send(
                new ShowMapaRoleplay("ShowMapaRoleplay", hasData, hasData ? mapsArray : null, getListTypesRooms(type)));
    }

    private JsonObject buildRoomJson(Room room, int index, int currentRoomId, int type, int random) {
        HabboInfo owner = HabboManager.getOfflineHabboInfo(room.getOwnerId());
        JsonObject obj = new JsonObject();

        if (index == 0) {
            obj.addProperty("counts", 0);
            obj.addProperty("room_active", currentRoomId);
            obj.addProperty("action_name", "GoToRoomTaxi");
            if (type != 0)
                obj.addProperty("type_id", type);
        }

        obj.addProperty("room_users_count", room.getUserCount());
        obj.addProperty("room_name", room.getName());
        obj.addProperty("room_owner", owner.getUsername());
        obj.addProperty("room_owner_look", look_base.replace("%LOOK%", owner.getLook()));
        obj.addProperty("room_id", room.getId());
        obj.addProperty("room_img", url_photos_base + room.getId() + ".png?v" + random);
        obj.addProperty("room_img_replace", global_url_images + "media/roomThumbnail.png");

        return obj;
    }

    public JsonArray getListTypesRooms(int activeType) {
        JsonArray typesArray = new JsonArray();
        for (int i = 0; i < types.length; i++) {
            JsonObject obj = new JsonObject();
            obj.addProperty("type", types[i]);
            obj.addProperty("type_name", type_titles[i]);
            obj.addProperty("name_real", type_names[i]);
            obj.addProperty("desc_real", type_descs[i]);
            obj.addProperty("type_id_active", activeType);
            obj.addProperty("action_active", (i == 5) ? 1 : 0);
            obj.addProperty("type_action_name", "SelectMapaRoleplay");
            obj.addProperty("url_img_content", url_images + "media/map.png");
            typesArray.add(obj);
        }
        return typesArray;
    }
}