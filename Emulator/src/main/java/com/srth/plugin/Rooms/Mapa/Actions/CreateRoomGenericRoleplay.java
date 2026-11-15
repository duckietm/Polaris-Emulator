package com.srth.plugin.Rooms.Mapa.Actions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.rooms.RoomRelativeMapComposer;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Rooms.RoomObjet;
import com.srth.plugin.habbo.HabboRp;

public class CreateRoomGenericRoleplay {

    public CreateRoomGenericRoleplay() {

    }

    public void New(Habbo habbo) {
        String layout = "nueva_calle";
        if (Emulator.getGameEnvironment().getRoomManager().layoutExists("roleplay_" + layout) == true) {

            try {
                HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
                // Mostrar proceso
                habboRp.getVidaHambreDormir().showTimeProcess("RoleplayWebsocketsRoomsInfo/actions/mapa2.png?v2",
                        RoleplayWebsocketsRoomsInfo.getTexts().getValue("action_process_create_room_generic_roleplay"),
                        10, true);
            } catch (Exception e) {
            }

            RoomObjet roomCreate = RoleplayWebsocketsRoomsInfo.getRoomsRoleplayManager().getRoom(layout);

            String convertName = layout.toUpperCase().charAt(0)
                    + layout.substring(1, layout.length()).toLowerCase().replace("_", " ");
            String nombre_sala = "[RP] " + convertName;
            String layout_sala = "roleplay_" + roomCreate.getNameType();

            Room room = Emulator.getGameEnvironment().getRoomManager().createRoomForHabbo(habbo, nombre_sala, "",
                    layout_sala, 10, RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.category.roleplay.id"),
                    1);
            room.setWallSize(-2);
            room.setHideWall(true);
            room.run();

            JsonParser parser = new JsonParser();
            // Obtain Array
            JsonArray gsonArr = parser.parse(roomCreate.getJson()).getAsJsonArray();
            // for each element of array
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
                Item baseItem = Emulator.getGameEnvironment().getItemManager().getItem(itemName);

                // Object Constructor
                HabboItem habboItem = Emulator.getGameEnvironment().getItemManager().createItem(
                        habbo.getHabboInfo().getId(),
                        baseItem,
                        0,
                        0,
                        "");
                habboItem.setRoomId(room.getId());
                habboItem.setX((short) x);
                habboItem.setY((short) y);
                habboItem.setZ(z);
                habboItem.setRotation(rot);
                habboItem.setExtradata(extra_data);
                habboItem.needsUpdate(true);
                habboItem.run();
            }

            if (room.isLoaded() && room.getUserCount() > 0 && room.getLayout() != null) {
                room.sendComposer(new RoomRelativeMapComposer(room).compose());
            }

            Emulator.getGameEnvironment().getItemManager().loadItems();

            try {
                // Enviar a la sala
                new GoToRoomTaxi().goTo(habbo, room.getId());
            } catch (Exception e) {
            }

        } else {
            RoleplayWebsocketsRoomsInfo.utilities().LOGGER_ERROR("No existe el layout registralo.");
        }

    }

}
