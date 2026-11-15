package com.srth.plugin.Tutorial.eventos.ActionEvents;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.rooms.RoomRelativeMapComposer;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Rooms.RoomObjet;
import com.srth.plugin.Rooms.Mapa.Actions.GoToRoomTaxi;
import com.srth.plugin.Rooms.Mapa.Taxi.ControllerRecorridosTaxi;
import com.srth.plugin.Rooms.Mapa.Taxi.RecorridoTaxi;
import com.srth.plugin.habbo.HabboRp;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

public class ActionCreateHome {

        private Habbo habbo;

        public ActionCreateHome(Habbo habbo) throws SQLException {

                this.habbo = habbo;

                if (this.habbo != null) {

                        HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

                        String name = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_room.create.name")
                                        .replace("%username%", habbo.getHabboInfo().getUsername());
                        String description = RoleplayWebsocketsRoomsInfo.getConfig()
                                        .getValue("global_room.create.descripcion")
                                        .replace("%username%", habbo.getHabboInfo().getUsername());

                        int maxUsers = RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.create.maxusers");
                        int tradeType = RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.create.tradetype");

                        RoomObjet roomHome = RoleplayWebsocketsRoomsInfo.getRoomsRoleplayManager().getRoom("home_user");

                        if (Emulator.getGameEnvironment().getRoomManager().layoutExists("roleplay_home_user") == true) {

                                // RECORRIDO DE COMPROBACION PARA TODOS LOS FURNIS
                                JsonParser parser = new JsonParser();
                                JsonArray gsonArr = parser.parse(roomHome.getJson()).getAsJsonArray();
                                int cantidadFurnis = 0;
                                int furnisExistentes = 0;
                                for (JsonElement obj : gsonArr) {
                                        cantidadFurnis++;
                                        // Object of array
                                        JsonObject gsonObj = obj.getAsJsonObject();
                                        // Primitives elements of object
                                        String itemName = gsonObj.get("item_base_name").getAsString();
                                        Item baseItem = Emulator.getGameEnvironment().getItemManager()
                                                        .getItem(itemName);
                                        if (baseItem != null) {
                                                furnisExistentes++;
                                        }
                                }
                                if (furnisExistentes < cantidadFurnis) {
                                        habbo.alert("¡No se encuentran la cantidad de furnis necesarios para crear esta sala! Existen: "
                                                        + furnisExistentes + " deberian ser: " + cantidadFurnis);
                                        return;
                                }

                                Room room = Emulator.getGameEnvironment().getRoomManager()
                                                .createRoomForHabbo(habbo, name, description,
                                                                "roleplay_home_user", maxUsers,
                                                                RoleplayWebsocketsRoomsInfo.getConfig().getInt(
                                                                                "global_room.category.roleplay.id"),
                                                                tradeType);
                                room.setWallSize(-2);
                                room.setHideWall(false);
                                room.run();
                                Emulator.getGameEnvironment().getRoomManager().loadLayout("roleplay_home_user",
                                                room);

                                for (JsonElement obj : gsonArr) {
                                        // Object of array
                                        JsonObject gsonObj = obj.getAsJsonObject();
                                        // Primitives elements of object
                                        String itemName = gsonObj.get("item_base_name").getAsString();
                                        int x = gsonObj.get("x").getAsInt();
                                        int y = gsonObj.get("y").getAsInt();
                                        double z = gsonObj.get("z").getAsDouble();
                                        int rot = gsonObj.get("rot").getAsInt();
                                        String extra_data = gsonObj.get("extra_data").getAsString();
                                        Item baseItem = Emulator.getGameEnvironment().getItemManager()
                                                        .getItem(itemName);

                                        // Object Constructor
                                        HabboItem habboItem = Emulator.getGameEnvironment().getItemManager().createItem(
                                                        habbo.getHabboInfo().getId(),
                                                        baseItem,
                                                        0,
                                                        0,
                                                        extra_data);
                                        habboItem.setRoomId(room.getId());
                                        this.updateItemRoomId(habboItem.getId(), room.getId());

                                        habboItem.setX((short) x);
                                        habboItem.setY((short) y);
                                        habboItem.setZ(z);
                                        habboItem.setRotation(rot);

                                        habboItem.setExtradata(extra_data);
                                        this.updateItemExtradata(habboItem.getId(), extra_data);

                                        if (!habboItem.getBaseItem().getName().equalsIgnoreCase("ads_background")) {
                                                habboItem.needsUpdate(true);
                                                habboItem.run();
                                        } else {
                                                habboItem.setExtradata(extra_data);
                                                this.updateItemExtradata(habboItem.getId(), extra_data);

                                                habboItem.needsUpdate(true);
                                                habboItem.run();

                                                habboItem.setExtradata(extra_data);
                                                this.updateItemExtradata(habboItem.getId(), extra_data);
                                        }
                                }

                                // Por defecto 634 // Personalizado 750
                                // int enable_taxi = RoleplayWebsocketsRoomsInfo.getConfig()
                                // .getInt("enable_taxi_id");
                                // habbo.getHabboInfo().getCurrentRoom().giveEffect(habbo,
                                // Integer.valueOf(enable_taxi),
                                // -1);

                                Emulator.getGameEnvironment().getItemManager().loadItems();

                                if (habboRp.getValue("global.home.room.rp.id") == "none") {
                                        habboRp.checkSettings("global.home.room.rp.id", room.getId());
                                } else {
                                        habboRp.updateData("global.home.room.rp.id", room.getId() + "");
                                }

                                habbo.getHabboInfo().setHomeRoom(room.getId());

                                habboRp.showMessage("RoleplayWebsocketsRoomsInfo/tutorial/casa.png",
                                                RoleplayWebsocketsRoomsInfo.getTexts()
                                                                .getValue("action.tutorial.homerp.recibed"));

                                Emulator.getThreading().run(() -> {

                                        Room room_visit = Emulator.getGameEnvironment().getRoomManager()
                                                        .loadRoom(room.getId());

                                        // String MSG1 = RoleplayWebsocketsRoomsInfo.getTexts()
                                        // .getValue("window_mapa.msg_visit_exitosa")
                                        // .replace("%room_name%", room_visit.getName());
                                        // habbo.talk(MSG1, RoomChatMessageBubbles.NORMAL);

                                        ControllerRecorridosTaxi controllerRecorridosTaxi = RoleplayWebsocketsRoomsInfo
                                                        .getControllerRecorridosTaxi();
                                        RecorridoTaxi recorrido = controllerRecorridosTaxi
                                                        .getRecorridoTaxi(habbo);
                                        if (recorrido == null) {
                                                controllerRecorridosTaxi.setRecorridoTaxi(habbo,
                                                                room_visit.getId());
                                        }

                                        habbo.getHabboInfo().getCurrentRoom().giveEffect(habbo, Integer.valueOf(0),
                                                        -1);

                                }, 1000);

                        } else {
                                RoleplayWebsocketsRoomsInfo.utilities()
                                                .LOGGER_ERROR("No existe el layout: roleplay_home_user registralo.");
                        }
                }
        }

        public void updateItemExtradata(int id_item, String extra_data) {
                try (Connection conn = Emulator.getDatabase().getDataSource().getConnection()) {
                        try (PreparedStatement statement = conn
                                        .prepareStatement(
                                                        "UPDATE items SET extra_data= ? WHERE id = ?")) {
                                statement.setString(1, extra_data);
                                statement.setInt(2, id_item);
                                statement.executeUpdate();
                                statement.close();
                        }
                } catch (SQLException exception) {
                        exception.printStackTrace();
                }
        }

        public void updateItemRoomId(int id_item, int roomID) {
                try (Connection conn = Emulator.getDatabase().getDataSource().getConnection()) {
                        try (PreparedStatement statement = conn
                                        .prepareStatement(
                                                        "UPDATE items SET room_id= ? WHERE id = ?")) {
                                statement.setInt(1, roomID);
                                statement.setInt(2, id_item);
                                statement.executeUpdate();
                                statement.close();
                        }
                } catch (SQLException exception) {
                        exception.printStackTrace();
                }
        }
}
