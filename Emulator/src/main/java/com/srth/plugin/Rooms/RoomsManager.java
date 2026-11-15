package com.srth.plugin.Rooms;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import javax.imageio.ImageIO;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.RoomRelativeMapComposer;
import com.eu.habbo.plugin.events.furniture.FurnitureToggleEvent;
import com.eu.habbo.plugin.events.users.UserTakeStepEvent;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Rooms.Cycleables.RoomCafeteria;
import com.srth.plugin.Rooms.Cycleables.RoomPesca;
import com.srth.plugin.Rooms.Mapa.MapaManager;
import com.srth.plugin.Rooms.Tyles.RoomTyleObjet;
import com.srth.plugin.Rooms.Tyles.RoomTylesManager;
import com.srth.plugin.Rooms.Types.Basurero;
import com.srth.plugin.Rooms.Types.Cafeteria;
import com.srth.plugin.Rooms.Types.CalleDeLosRecursos;
import com.srth.plugin.Rooms.Types.CalleDelLago;
import com.srth.plugin.Rooms.Types.CalleDelPuente;
import com.srth.plugin.Rooms.Types.CallesPeligrosas1;
import com.srth.plugin.Rooms.Types.CallesPeligrosas2;
import com.srth.plugin.Rooms.Types.CallesPeligrosas3;
import com.srth.plugin.Rooms.Types.CentroPenitenciario;
import com.srth.plugin.Rooms.Types.HomeUser;
import com.srth.plugin.Rooms.Types.HospitalRP;
import com.srth.plugin.Rooms.Types.NuevaCalle;
import com.srth.plugin.Rooms.Types.Supermercado;
import com.srth.plugin.Rooms.Utils.TextRoomsCreate;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.ConfirmEvents.confirmselectevents.ConfirmSelectEventsManager;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.communication.Outcoming;
import com.srth.plugin.websocket.communication.outgoing.messages.MessageManager;
import com.srth.plugin.websocket.communication.outgoing.messages.MessageManagerDouble;

import gnu.trove.map.hash.THashMap;

public class RoomsManager {

    HabboRp habboRp;
    THashMap<String, RoomObjet> rooms = new THashMap<String, RoomObjet>();
    THashMap<Habbo, HabboItem> item_select = new THashMap<Habbo, HabboItem>();
    public boolean allowmove = true;

    THashMap<Integer, RoomObjet> mapRoomsIds = new THashMap<Integer, RoomObjet>();

    RoomTylesManager roomTylesManager = new RoomTylesManager();

    public RoomsManager() {

        new TextRoomsCreate().load();

        // Estableciendo un nuevo cycleable
        RoomPesca roomPesca = new RoomPesca();
        Emulator.getThreading().run(roomPesca);

        RoomCafeteria roomCafeteria = new RoomCafeteria();
        Emulator.getThreading().run(roomCafeteria);
    }

    public THashMap<Integer, RoomObjet> getRoomsIds() {
        return mapRoomsIds;
    }

    public void update() {
        rooms.clear();
        // Registrar aca todos los tipos de salas
        new HomeUser("home_user");
        new NuevaCalle("nueva_calle");
        new HospitalRP("hospital");
        new CallesPeligrosas1("calles_peligrosas_1");
        new CallesPeligrosas2("calles_peligrosas_2");
        new CallesPeligrosas3("calles_peligrosas_3");
        new Supermercado("supermercado");
        new CalleDelLago("calle_del_lago");
        new CentroPenitenciario("centro_penitenciario");
        new Basurero("basurero");
        new CalleDelPuente("calle_del_puente");
        new Cafeteria("cafeteria");
        new CalleDeLosRecursos("calle_de_los_recursos");

        for (RoomObjet roomsObjets : this.rooms.values()) {
            Room room = Emulator.getGameEnvironment().getRoomManager().loadRoom(roomsObjets.getRoomId());
            if (room != null) {
                this.mapRoomsIds.put(room.getId(), roomsObjets);
            }
        }

    }

    public RoomObjet getRoom(String name) {
        return this.getListRooms().get(name);
    }

    public THashMap<String, RoomObjet> getListRooms() {
        return rooms;
    }

    public void createRoomType(String type, RoomObjet objet) {
        if (rooms.get(type) == null) {
            rooms.put(type, objet);
        }
    }

    public void showVentainCreateRooms(Habbo habbo) throws SQLException {

        String name_key = "create_rooms_roleplay";
        this.habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

        if (this.habboRp.getHabboClient() == null) {
            return;
        }
        JsonArray ArrayUno = new JsonArray();
        JsonObject jsonObject;

        jsonObject = new JsonObject();
        jsonObject.addProperty("title", RoleplayWebsocketsRoomsInfo.getTexts().getValue(name_key + "_ventana_title"));

        String url_hotel_base = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.url.hotel.base");
        String global_url_images = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url_images")
                .replace("%url.hotel.base%", url_hotel_base);
        String url_images = global_url_images + "/frank/";

        jsonObject.addProperty("image_frank",
                url_images + "/frank_hello.gif");

        jsonObject.addProperty("mensaje",
                RoleplayWebsocketsRoomsInfo.getTexts().getValue(name_key + "_ventana_text_inicio"));
        jsonObject.addProperty("buttons_quest",
                RoleplayWebsocketsRoomsInfo.getTexts().getValue(name_key + "_ventana_text_buttones_quest"));
        jsonObject.addProperty("buttons_exist_text",
                RoleplayWebsocketsRoomsInfo.getTexts().getValue(name_key + "_ventana_room_exist"));
        jsonObject.addProperty("cantidad_botones", this.getListRooms().size());
        ArrayUno.add(jsonObject);

        JsonArray Buttons = new JsonArray();
        JsonObject jsonObjectButtons;

        jsonObjectButtons = new JsonObject();
        jsonObjectButtons.addProperty("button_name",
                this.habboRp.getConstructorSalas() == true ? "Desactivar constructor de salas"
                        : "Activar constructor de salas");
        jsonObjectButtons.addProperty("button_event", "ConfirmButton_ConstructorRoomsRoleplay");
        jsonObjectButtons.addProperty("button_active", 1);
        Buttons.add(jsonObjectButtons);

        jsonObjectButtons = new JsonObject();
        jsonObjectButtons.addProperty("button_name",
                "------------------------------------------------------------------------------------------");
        jsonObjectButtons.addProperty("button_event", "");
        jsonObjectButtons.addProperty("button_active", 1);
        Buttons.add(jsonObjectButtons);

        if (!this.getListRooms().isEmpty()) {
            for (RoomObjet roomType : this.getListRooms().values()) {
                if (roomType.getCreateButtonActive() == true) {
                    jsonObjectButtons = new JsonObject();
                    jsonObjectButtons.addProperty("button_name", roomType.getNamePublic());
                    jsonObjectButtons.addProperty("button_event", "ConfirmButton_" + roomType.getNameType());

                    if (RoleplayWebsocketsRoomsInfo.getConfig()
                            .getValue(roomType.getNameType() + "_room_exist") == "none"
                            || !RoleplayWebsocketsRoomsInfo.getConfig().getValue(roomType.getNameType() + "_room_exist")
                                    .equalsIgnoreCase("true")) {
                        jsonObjectButtons.addProperty("button_active", 1);
                    } else {
                        jsonObjectButtons.addProperty("button_active", 0);
                    }
                    Buttons.add(jsonObjectButtons);
                }
            }

            jsonObjectButtons = new JsonObject();
            jsonObjectButtons.addProperty("button_name",
                    "------------------------------------------------------------------------------------------");
            jsonObjectButtons.addProperty("button_event", "");
            jsonObjectButtons.addProperty("button_active", 1);
            Buttons.add(jsonObjectButtons);

            jsonObjectButtons = new JsonObject();
            jsonObjectButtons.addProperty("button_name", "Activar/Desactivar configurar teleports");
            jsonObjectButtons.addProperty("button_event", "ConfirmButton_configure_teleports");
            jsonObjectButtons.addProperty("button_active", 1);
            Buttons.add(jsonObjectButtons);

            if (habbo.getHabboInfo().getUsername().contains("SrThrone")) {

                jsonObjectButtons = new JsonObject();
                jsonObjectButtons.addProperty("button_name", "Activar/Desactivar vista de coordenadas");
                jsonObjectButtons.addProperty("button_event", "ConfirmButton_ViewCoordenadas");
                jsonObjectButtons.addProperty("button_active", 1);
                Buttons.add(jsonObjectButtons);

                jsonObjectButtons = new JsonObject();
                jsonObjectButtons.addProperty("button_name", "Mostrar gestor de salas");
                jsonObjectButtons.addProperty("button_event", "ConfirmButton_GestorRoomsRoleplay");
                jsonObjectButtons.addProperty("button_active", 1);
                Buttons.add(jsonObjectButtons);

                jsonObjectButtons = new JsonObject();
                jsonObjectButtons.addProperty("button_name",
                        "Crear layout (name_room_prod): "
                                + RoleplayWebsocketsRoomsInfo.getConfig().getValue("name_room_prod")
                                + "");
                jsonObjectButtons.addProperty("button_event", "eventRealiceFunctionProd");
                jsonObjectButtons.addProperty("button_active", 1);
                Buttons.add(jsonObjectButtons);

                jsonObjectButtons = new JsonObject();
                jsonObjectButtons.addProperty("button_name",
                        "------------------------------------------------------------------------------------------");
                jsonObjectButtons.addProperty("button_event", "");
                jsonObjectButtons.addProperty("button_active", 1);
                Buttons.add(jsonObjectButtons);

            }

            jsonObjectButtons = new JsonObject();
            jsonObjectButtons.addProperty("button_name", "Crear conexiones flechas");
            jsonObjectButtons.addProperty("button_event", "ConfirmButton_CreateConexionesFlechas");
            jsonObjectButtons.addProperty("button_active", 1);
            Buttons.add(jsonObjectButtons);

        }

        this.habboRp.getHabboClient().send(new MessageManagerDouble("MessageManager", true,
                Outcoming.ViewFrankMultipleSelectCustom, ArrayUno, Buttons));
    }

    public Room CreateNewRoomLayout(Habbo habbo, String layout) {
        if (Emulator.getGameEnvironment().getRoomManager().layoutExists("roleplay_" + layout) == true) {

            try {
                HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
                // Mostrar proceso
                habboRp.getVidaHambreDormir().showTimeProcess("RoleplayWebsocketsRoomsInfo/actions/mapa2.png?v2",
                        RoleplayWebsocketsRoomsInfo.getTexts().getValue("action_process_create_room_generic_roleplay"),
                        120, true);
            } catch (Exception e) {
            }

            RoomObjet roomCreate = this.getRoom(layout);

            // RECORRIDO DE COMPROBACION PARA TODOS LOS FURNIS
            JsonParser parser = new JsonParser();
            JsonArray gsonArr = parser.parse(roomCreate.getJson()).getAsJsonArray();
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
                } else {
                    RoleplayWebsocketsRoomsInfo.utilities()
                            .LOGGER("----------------------------------------------------");
                    RoleplayWebsocketsRoomsInfo.utilities().LOGGER(itemName + ", no se encuentra");
                    RoleplayWebsocketsRoomsInfo.utilities()
                            .LOGGER("----------------------------------------------------");
                }
            }

            if (furnisExistentes < cantidadFurnis) {
                habbo.alert("¡No se encuentran la cantidad de furnis necesarios para crear esta sala! Existen: "
                        + furnisExistentes + " deberian ser: " + cantidadFurnis);
                return null;
            }

            String convertName = layout.toUpperCase().charAt(0)
                    + layout.substring(1, layout.length()).toLowerCase().replace("_", " ");
            String nombre_sala = "[RP] " + convertName;
            String layout_sala = "roleplay_" + roomCreate.getNameType();

            Room room = Emulator.getGameEnvironment().getRoomManager().createRoomForHabbo(habbo, nombre_sala, "",
                    layout_sala, 10, RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.category.roleplay.id"),
                    1);
            room.setHideWall(true);
            room.setWallSize(-2);
            room.run();

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
                Item baseItem = Emulator.getGameEnvironment().getItemManager()
                        .getItem(itemName);
                if (baseItem != null) {
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
                } else {
                    RoleplayWebsocketsRoomsInfo.utilities()
                            .LOGGER("----------------------------------------------------");
                    RoleplayWebsocketsRoomsInfo.utilities().LOGGER(itemName + ", no se encuentra");
                    RoleplayWebsocketsRoomsInfo.utilities()
                            .LOGGER("----------------------------------------------------");
                }
            }

            if (room.isLoaded() && room.getUserCount() > 0 && room.getLayout() != null) {
                room.sendComposer(new RoomRelativeMapComposer(room).compose());
            }

            Emulator.getGameEnvironment().getItemManager().loadItems();

            roomCreate.setRoomId(room.getId());

            room.giveEffect(habbo, Integer.valueOf(634), -1);
            Emulator.getThreading().run(() -> {
                try {
                    habboRp.ocultMessage();
                } catch (SQLException e) {
                }
                habbo.goToRoom(room.getId());
            }, 5000);

            return room;
        } else {
            RoleplayWebsocketsRoomsInfo.utilities().LOGGER_ERROR("No existe este layout registralo, type: " + layout);
            return null;
        }
    }

    public void ocultVentainCreateRooms(Habbo habbo) throws SQLException {

        this.habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

        if (this.habboRp.getHabboClient() == null) {
            return;
        }

        this.habboRp.getHabboClient()
                .send(new MessageManager("MessageManager", false, Outcoming.ViewFrankMultipleSelectCustom, null));
    }

    public void updateOrRegisterConfig(String name_layout) {
        RoleplayWebsocketsRoomsInfo.getConfig().checkSettings(name_layout + "_room_exist", "true");
        RoleplayWebsocketsRoomsInfo.getConfig().updateData(name_layout + "_room_exist", "true");
    }

    public void eventStepOnTileEvent(UserTakeStepEvent ev) {
    }

    public void eventUserClickTile(MessageHandler ev) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(ev.client.getHabbo());
            if (habboRp.getCacheInt("configure_teleports") == 1) {

                if (this.allowmove == false) {
                    ev.isCancelled = true;
                }

            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void getReceibedToggleEvent(FurnitureToggleEvent event) {
        try {

            HabboItem item = event.furniture;
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(event.habbo);

            if (habboRp.getCacheInt("configure_teleports") == 1) {

                if (item.getBaseItem().getName().contains("room_switcher_")) {

                    RoleplayWebsocketsRoomsInfo.utilities().LOGGER(this.allowmove + " Allowmove");
                    if (this.allowmove == true) {
                        event.setCancelled(true);
                    }

                    if (habboRp.getCacheInt("teleport_go") == 0) {

                        this.allowmove = false;

                        habboRp.registerCache("teleport_go", item.getId() + "");

                        habboRp.getHabbo().whisper("Teleport go: " + item.getId());
                        habboRp.getHabbo().whisper("Ahora presiona el teleport de otra sala para conectarlos.");

                        Emulator.getThreading().run(() -> {
                            this.allowmove = true;
                        }, 2000);

                    } else {
                        if (habboRp.getCacheInt("teleport_go") != item.getId()) {

                            habboRp.registerCache("teleport_to", item.getId() + "");

                            habboRp.getHabbo().whisper("Teleport to: " + item.getId());

                            habboRp.getHabbo().whisper("Confirma que quieres realizar la conexion.");
                            new ConfirmSelectEventsManager().showConfirmAlert(habboRp.getHabbo(),
                                    "createConexionTitlesTeleport");

                            this.allowmove = true;
                        }
                    }

                    // // Congelar
                    // event.habbo.getRoomUnit().setCanWalk(false);

                    // Emulator.getThreading().run(() -> {
                    // // Descongelar
                    // event.habbo.getRoomUnit().setCanWalk(true);
                    // }, 2000);

                }
            }
        } catch (Exception e) {
        }
    }

    public void eventUserMove(Habbo habbo) {
        try {

            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

            if (habboRp.getCacheString("view_coordenadas") != "none") {

                JsonArray ArrayUno = new JsonArray();
                ArrayUno.add(this.getJsonObjet(habbo));

                habboRp.getHabboClient()
                        .send(new MessageManager("MessageManager", true, Outcoming.CoordenadasView, ArrayUno));
            }
        } catch (Exception ev) {
        }

    }

    public void eventUserFurniDoubleClick(FurnitureToggleEvent e) {
        try {

            Habbo habbo = e.habbo;

            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            if (habbo.getHabboInfo().getCurrentRoom().getCategory() == RoleplayWebsocketsRoomsInfo.getConfig()
                    .getInt("global_room.category.roleplay.id")) {

                // Si no tienes activo el constructor de salas y intentas cambiar el estado
                if (habboRp.getConstructorSalas() == false) {
                    if (e.furniture.getBaseItem().getInteractionType().getName().equalsIgnoreCase("default")
                            || e.furniture.getBaseItem().getInteractionType().getName()
                                    .equalsIgnoreCase("multiheight")) {
                        e.setCancelled(true);
                    }
                }

                if (habboRp.getCacheString("view_coordenadas") != "none") {

                    if (item_select.get(habbo) == null) {
                        item_select.put(habbo, e.furniture);
                    } else {
                        if (item_select.get(habbo) != e.furniture) {
                            item_select.remove(habbo);
                            item_select.put(habbo, e.furniture);
                        }
                    }

                    JsonArray ArrayUno = new JsonArray();
                    ArrayUno.add(this.getJsonObjet(habbo));

                    habboRp.getHabboClient()
                            .send(new MessageManager("MessageManager", true, Outcoming.CoordenadasView, ArrayUno));
                }
            }
        } catch (Exception ev) {
        }

    }

    public JsonObject getJsonObjet(Habbo habbo) {
        JsonObject jsonObject;
        jsonObject = new JsonObject();

        jsonObject = new JsonObject();
        jsonObject.addProperty("event_x", habbo.getRoomUnit().getX());
        jsonObject.addProperty("event_y", habbo.getRoomUnit().getY());
        jsonObject.addProperty("event_z", habbo.getRoomUnit().getZ());
        jsonObject.addProperty("event_room_id", habbo.getHabboInfo().getCurrentRoom().getId());
        jsonObject.addProperty("event_roomUnitId", habbo.getRoomUnit().getId());

        if (item_select.get(habbo) != null) {

            HabboItem item = item_select.get(habbo);

            jsonObject.addProperty("furni_select", item.getId());
            jsonObject.addProperty("furni_item_base_name", item.getBaseItem().getName());
            jsonObject.addProperty("furni_x", item.getX());
            jsonObject.addProperty("furni_y", item.getY());
            jsonObject.addProperty("furni_z", item.getZ());

        } else {
            jsonObject.addProperty("furni_select", -1);
        }

        return jsonObject;

    }

    public void showInfoSala(Habbo habbo, Room room) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

            if (room == null || habboRp == null)
                return;

            RoomObjet roomObjet = mapRoomsIds.get(room.getId());
            if (roomObjet == null) {
                if (!room.getName().contains("[CASA]")) {
                    return;
                }
            }

            JsonArray arrayUno = new JsonArray();
            JsonObject jsonObject = new JsonObject();

            jsonObject.addProperty("id", room.getId());

            // ------------------------

            // Lógica de la imagen
            String basePath = Emulator.getConfig().getValue("imager.location.output.thumbnail");
            String fileName = room.getId() + ".png";
            File imageFile = new File(basePath, fileName);

            if (imageFile.exists() && !imageFile.isDirectory()) {
                String camera_url = Emulator.getConfig().getValue("camera.url") + "/thumbnail/";
                jsonObject.addProperty("img", camera_url + fileName);
            } else {
                String url_hotel_base = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.url.hotel.base");
                String img = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url_images")
                        .replace("%url.hotel.base%", url_hotel_base)
                        + "media/roomThumbnail.png";
                jsonObject.addProperty("img", img);
            }

            String formattedName;

            // Lógica de seguridad
            String horario = RoleplayWebsocketsRoomsInfo.getHorarioManager().getTimeHorario();
            boolean isSecure = false;

            // Si no es de dia
            if (horario.equalsIgnoreCase("noche")) {
                if (roomObjet != null && roomObjet.getSecure()) {
                    if (roomObjet.getNameType().startsWith("hospital")) {
                        isSecure = true;
                    } else {
                        isSecure = false;
                    }
                }
                // --- FILTRO DE NOMBRE ---
                // Convertimos "calles_peligrosas_3" a "Calles Peligrosas 3"
                formattedName = formatRoomName(room.getName());
            } else {
                // Cuando es de dia
                isSecure = true;
                formattedName = formatRoomName(room.getName());
            }

            if (roomObjet == null) {
                // Cuando es tu casa
                isSecure = true;
                formattedName = room.getName();
            }

            jsonObject.addProperty("name", formattedName);

            jsonObject.addProperty("secure", isSecure ? 1 : 0);
            arrayUno.add(jsonObject);
            habboRp.getHabboClient().send(new MessageManager("MessageManager", true, Outcoming.ShowInfoSala, arrayUno));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Convierte snake_case a Title Case (ej: calles_peligrosas_3 -> Calles
     * Peligrosas 3)
     */
    private String formatRoomName(String name) {
        if (name == null || name.isEmpty())
            return "";

        String nameFormat = name.replace("[RP]", "");
        // Reemplaza guiones bajos por espacios
        String[] words = nameFormat.replace("_", " ").split(" ");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (!word.isEmpty()) {
                // Primera letra a Mayúscula + resto de la palabra
                result.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1).toLowerCase())
                        .append(" ");
            }
        }
        return result.toString().trim();
    }

    public void ocultInfoSala(Habbo habbo) {
        try {

            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

            habboRp.getHabboClient()
                    .send(new MessageManager("MessageManager", false, Outcoming.ShowInfoSala, null));
        } catch (Exception e) {
        }

    }

    public void IncomingListActions(THashMap<String, Object> data, HabboClient wsclient) {
        try {

            String name = (String) data.get("name");
            if (name.equalsIgnoreCase("ConfirmButton_hospital")) {

                this.ocultVentainCreateRooms(wsclient.getHabbo());

                String name_layout = "hospital";
                Room room = this.CreateNewRoomLayout(wsclient.getHabbo(), name_layout);
                if (room != null) {
                    this.updateOrRegisterConfig(name_layout);
                    this.showVentainCreateRooms(wsclient.getHabbo());

                    // Establecer esta sala como el empleo nuevo
                    RoleplayWebsocketsRoomsInfo.getJobsManager().getJob("medico").setRoom(room.getId());

                    RoomObjet roomCreate = this.getRoom(name_layout);
                    roomCreate.registerRoomId(room.getId());
                    roomCreate.setRoomId(room.getId());
                }
            }

            if (name.equalsIgnoreCase("ConfirmButton_calles_peligrosas_3")) {

                this.ocultVentainCreateRooms(wsclient.getHabbo());

                String name_layout = "calles_peligrosas_3";
                Room room = this.CreateNewRoomLayout(wsclient.getHabbo(), name_layout);
                if (room != null) {
                    this.updateOrRegisterConfig(name_layout);
                    this.showVentainCreateRooms(wsclient.getHabbo());

                    // Establecer sala en el mapa
                    new MapaManager().setMapaRoom(name_layout, room.getId());

                    RoomObjet roomCreate = this.getRoom(name_layout);
                    roomCreate.registerRoomId(room.getId());
                    roomCreate.setRoomId(room.getId());
                }
            }

            if (name.equalsIgnoreCase("ConfirmButton_calles_peligrosas_2")) {

                this.ocultVentainCreateRooms(wsclient.getHabbo());

                String name_layout = "calles_peligrosas_2";
                Room room = this.CreateNewRoomLayout(wsclient.getHabbo(), name_layout);
                if (room != null) {
                    this.updateOrRegisterConfig(name_layout);
                    this.showVentainCreateRooms(wsclient.getHabbo());

                    // Establecer sala en el mapa
                    new MapaManager().setMapaRoom(name_layout, room.getId());

                    RoomObjet roomCreate = this.getRoom(name_layout);
                    roomCreate.registerRoomId(room.getId());
                    roomCreate.setRoomId(room.getId());
                }
            }

            if (name.equalsIgnoreCase("ConfirmButton_calles_peligrosas_1")) {

                this.ocultVentainCreateRooms(wsclient.getHabbo());

                String name_layout = "calles_peligrosas_1";
                Room room = this.CreateNewRoomLayout(wsclient.getHabbo(), name_layout);
                if (room != null) {
                    this.updateOrRegisterConfig(name_layout);
                    this.showVentainCreateRooms(wsclient.getHabbo());

                    // Establecer sala en el mapa
                    new MapaManager().setMapaRoom(name_layout, room.getId());

                    RoomObjet roomCreate = this.getRoom(name_layout);
                    roomCreate.registerRoomId(room.getId());
                    roomCreate.setRoomId(room.getId());
                }
            }

            if (name.equalsIgnoreCase("ConfirmButton_supermercado")) {

                this.ocultVentainCreateRooms(wsclient.getHabbo());

                String name_layout = "supermercado";
                Room room = this.CreateNewRoomLayout(wsclient.getHabbo(), name_layout);
                if (room != null) {

                    this.updateOrRegisterConfig(name_layout);
                    this.showVentainCreateRooms(wsclient.getHabbo());

                    // Establecer sala en el mapa
                    new MapaManager().setMapaRoom(name_layout, room.getId());

                    RoomObjet roomCreate = this.getRoom(name_layout);
                    roomCreate.registerRoomId(room.getId());
                    roomCreate.setRoomId(room.getId());
                }
            }

            if (name.equalsIgnoreCase("ConfirmButton_calle_del_lago")) {

                this.ocultVentainCreateRooms(wsclient.getHabbo());

                String name_layout = "calle_del_lago";
                Room room = this.CreateNewRoomLayout(wsclient.getHabbo(), name_layout);
                if (room != null) {

                    this.updateOrRegisterConfig(name_layout);
                    this.showVentainCreateRooms(wsclient.getHabbo());

                    // Establecer sala en el mapa
                    new MapaManager().setMapaRoom(name_layout, room.getId());

                    RoomObjet roomCreate = this.getRoom(name_layout);
                    roomCreate.registerRoomId(room.getId());
                    roomCreate.setRoomId(room.getId());
                }
            }

            if (name.equalsIgnoreCase("ConfirmButton_basurero")) {

                this.ocultVentainCreateRooms(wsclient.getHabbo());

                String name_layout = "basurero";
                Room room = this.CreateNewRoomLayout(wsclient.getHabbo(), name_layout);
                if (room != null) {

                    this.updateOrRegisterConfig(name_layout);
                    this.showVentainCreateRooms(wsclient.getHabbo());

                    // Establecer esta sala como el empleo nuevo
                    RoleplayWebsocketsRoomsInfo.getJobsManager().getJob("aseo").setRoom(room.getId());

                    RoomObjet roomCreate = this.getRoom(name_layout);
                    roomCreate.registerRoomId(room.getId());
                    roomCreate.setRoomId(room.getId());
                }
            }

            if (name.equalsIgnoreCase("ConfirmButton_centro_penitenciario")) {

                this.ocultVentainCreateRooms(wsclient.getHabbo());

                String name_layout = "centro_penitenciario";
                Room room = this.CreateNewRoomLayout(wsclient.getHabbo(), name_layout);
                if (room != null) {

                    this.updateOrRegisterConfig(name_layout);
                    this.showVentainCreateRooms(wsclient.getHabbo());

                    // Establecer esta sala como el empleo nuevo
                    RoleplayWebsocketsRoomsInfo.getJobsManager().getJob("police").setRoom(room.getId());

                    RoomObjet roomCreate = this.getRoom(name_layout);
                    roomCreate.registerRoomId(room.getId());
                    roomCreate.setRoomId(room.getId());
                }
            }

            if (name.equalsIgnoreCase("ConfirmButton_calle_del_puente")) {

                this.ocultVentainCreateRooms(wsclient.getHabbo());

                String name_layout = "calle_del_puente";
                Room room = this.CreateNewRoomLayout(wsclient.getHabbo(), name_layout);
                if (room != null) {

                    this.updateOrRegisterConfig(name_layout);
                    this.showVentainCreateRooms(wsclient.getHabbo());

                    // Establecer sala en el mapa
                    new MapaManager().setMapaRoom(name_layout, room.getId());

                    RoomObjet roomCreate = this.getRoom(name_layout);
                    roomCreate.registerRoomId(room.getId());
                    roomCreate.setRoomId(room.getId());

                }
            }

            if (name.equalsIgnoreCase("ConfirmButton_cafeteria")) {

                this.ocultVentainCreateRooms(wsclient.getHabbo());

                String name_layout = "cafeteria";
                Room room = this.CreateNewRoomLayout(wsclient.getHabbo(), name_layout);
                if (room != null) {

                    this.updateOrRegisterConfig(name_layout);
                    this.showVentainCreateRooms(wsclient.getHabbo());

                    // Establecer sala en el mapa
                    new MapaManager().setMapaRoom(name_layout, room.getId());

                    // Establecer esta sala como el empleo nuevo
                    RoleplayWebsocketsRoomsInfo.getJobsManager().getJob("cafetero").setRoom(room.getId());

                    RoomObjet roomCreate = this.getRoom(name_layout);
                    roomCreate.registerRoomId(room.getId());
                    roomCreate.setRoomId(room.getId());

                }
            }

            if (name.equalsIgnoreCase("ConfirmButton_calle_de_los_recursos")) {

                this.ocultVentainCreateRooms(wsclient.getHabbo());

                String name_layout = "calle_de_los_recursos";
                Room room = this.CreateNewRoomLayout(wsclient.getHabbo(), name_layout);
                if (room != null) {

                    this.updateOrRegisterConfig(name_layout);
                    this.showVentainCreateRooms(wsclient.getHabbo());

                    // Establecer sala en el mapa
                    new MapaManager().setMapaRoom(name_layout, room.getId());

                    RoomObjet roomCreate = this.getRoom(name_layout);
                    roomCreate.registerRoomId(room.getId());
                    roomCreate.setRoomId(room.getId());

                }
            }

            if (name.equalsIgnoreCase("ConfirmButton_configure_teleports")) {

                this.ocultVentainCreateRooms(wsclient.getHabbo());
                HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(wsclient.getHabbo());

                if (habboRp.getCacheString("configure_teleports") == "none") {

                    habboRp.registerCache("configure_teleports", "1");

                    wsclient.getHabbo().whisper("Activaste el modo configuracion de teleports..");
                    wsclient.getHabbo()
                            .whisper("Presiona el teleport inicial y luego el teleport secundario, para conectarlos.");
                } else {

                    wsclient.getHabbo().whisper("Desactivaste el modo configuracion de teleports..");
                    habboRp.removeCache("configure_teleports");
                    habboRp.removeCache("teleport_go");
                    habboRp.removeCache("teleport_to");
                }
            } else if (name.equalsIgnoreCase("ConfirmButton_ViewCoordenadas")) {

                this.ocultVentainCreateRooms(wsclient.getHabbo());
                HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(wsclient.getHabbo());

                if (habboRp.getCacheString("view_coordenadas") == "none") {
                    habboRp.registerCache("view_coordenadas", "1");
                    wsclient.getHabbo().whisper("Activaste el modo vista de coordenadas..");

                    this.eventUserMove(wsclient.getHabbo());
                } else {
                    wsclient.getHabbo().whisper("Desactivaste el modo vista de coordenadas..");
                    habboRp.removeCache("view_coordenadas");

                    habboRp.getHabboClient()
                            .send(new MessageManager("MessageManager", false, Outcoming.CoordenadasView, null));
                }

            } else if (name.equalsIgnoreCase("ConfirmButton_GestorRoomsRoleplay")) {

                this.ocultVentainCreateRooms(wsclient.getHabbo());

                habboRp.getHabboClient()
                        .send(new MessageManager("MessageManager", true, Outcoming.GestorDeSalasWindow, null));
            } else if (name.equalsIgnoreCase("ConfirmButton_ConstructorRoomsRoleplay")) {
                if (habboRp.getConstructorSalas() == false) {
                    habboRp.setConstructorSalas(true);
                    wsclient.getHabbo().whisper("Activaste el modo constructor de salas..");

                    // Comenzar interval y mostrar divs
                    habboRp.getHabboClient()
                            .send(new MessageManager("MessageManager", false, Outcoming.ExitRoom,
                                    null));
                } else {
                    habboRp.setConstructorSalas(false);
                    wsclient.getHabbo().whisper("Desactivaste el modo constructor de salas..");

                    // Comenzar interval y ocultar divs
                    habboRp.getHabboClient()
                            .send(new MessageManager("MessageManager", true, Outcoming.EnterRoomRP,
                                    null));
                }

                this.ocultVentainCreateRooms(wsclient.getHabbo());
            }

            if (name.equalsIgnoreCase("GestionarRoomIdData")) {
                int roomId = (int) ((double) data.get("action"));

                Room room = Emulator.getGameEnvironment().getRoomManager().loadRoom(roomId);
                room.loadData();
                room.run();

                this.ocultVentainCreateRooms(wsclient.getHabbo());

                // 1. Creamos un JsonArray para contener todos los furnis
                JsonArray furnisCollection = new JsonArray();

                for (HabboItem items : room.getFloorItems()) {
                    // 2. Creamos un objeto JSON por cada item
                    JsonObject itemJson = new JsonObject();
                    itemJson.addProperty("item_base_name", items.getBaseItem().getName());
                    itemJson.addProperty("x", items.getX());
                    itemJson.addProperty("y", items.getY());
                    itemJson.addProperty("z", items.getZ());
                    itemJson.addProperty("rot", items.getRotation());
                    itemJson.addProperty("extra_data", items.getExtradata());

                    // 3. Lo añadimos al array coleccionador
                    furnisCollection.add(itemJson);
                }

                // 4. Preparamos el mensaje final
                JsonArray arrayUno = new JsonArray();
                JsonObject jsonObject = new JsonObject();

                // Convertimos el array de furnis a String para el campo "furnis"
                jsonObject.addProperty("furnis", furnisCollection.toString());
                jsonObject.addProperty("room_layout", room.getLayout().getName()); // Ejemplo de layout
                jsonObject.addProperty("room_heightmap", room.getLayout().getHeightmap());
                jsonObject.addProperty("room_door_x", room.getLayout().getDoorX());
                jsonObject.addProperty("room_door_y", room.getLayout().getDoorY());
                jsonObject.addProperty("room_door_dir", room.getLayout().getDoorDirection());

                arrayUno.add(jsonObject);

                // 5. Enviamos al MessageManager (usando la estructura que ya tienes guardada)
                habboRp.getHabboClient().send(
                        new MessageManager("MessageManager", true, Outcoming.GestorDeSalasWindow, arrayUno));
            }

            if (name.equalsIgnoreCase("eventRealiceFunctionProd")) {
                new ConfirmSelectEventsManager().showConfirmAlert(wsclient.getHabbo(), "realiceFunctionPROD");
            }

            if (name.equalsIgnoreCase("ConfirmButton_CreateConexionesFlechas")) {
                roomTylesManager.load();

                roomTylesManager.updateAllTiles();
            }

        } catch (Exception e) {
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
