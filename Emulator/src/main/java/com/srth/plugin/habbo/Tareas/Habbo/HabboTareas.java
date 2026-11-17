package com.srth.plugin.habbo.Tareas.Habbo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.TreeMap;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.Tareas.Tarea;
import com.srth.plugin.habbo.Tareas.TareaType;
import com.srth.plugin.habbo.Tareas.Level.Level;
import com.srth.plugin.habbo.Tareas.Level.Reward;
import com.srth.plugin.websocket.communication.Outcoming;
import com.srth.plugin.websocket.communication.outgoing.messages.MessageManager;
import com.srth.plugin.websocket.communication.outgoing.messages.MessageManagerDoubleObjet;
import com.srth.plugin.websocket.communication.outgoing.messages.outworks.PlaySound;

import gnu.trove.map.hash.THashMap;

public class HabboTareas {

    Habbo habbo;
    HabboRp habboRp;
    THashMap<String, ProgresoTarea> progresos = new THashMap<String, ProgresoTarea>();
    int notifications;

    String url_hotel_base = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.url.hotel.base");
    String rutaImagesNotifications = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url.hotel.notifications")
            .replace("%url.hotel.base%", url_hotel_base) + "RoleplayWebsocketsRoomsInfo/";

    String global_url_images = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url_images")
            .replace("%url.hotel.base%", url_hotel_base);

    public HabboTareas(Habbo habbo) {
        this.habbo = habbo;
        try {
            this.habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            this.update();
        } catch (Exception e) {
        }
    }

    public void update() throws SQLException {
        this.progresos.clear();
        this.loadProgresos();
    }

    public int getNotification() {
        return notifications;
    }

    public void setNotification(int number) {
        notifications = number;
    }

    public void loadProgresos() throws SQLException {
        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection()) {
            try (PreparedStatement preparedStatement = conn
                    .prepareStatement("SELECT * FROM rp_pase_progresos WHERE user_id = ? ORDER BY id ASC")) {
                preparedStatement.setInt(1, habbo.getHabboInfo().getId());
                ResultSet set = preparedStatement.executeQuery();
                while (set.next()) {
                    Tarea tarea = RoleplayWebsocketsRoomsInfo.getTareasManager().getTareaById(set.getInt("tarea_id"));
                    if (tarea != null) {
                        this.progresos.put(tarea.getName(), new ProgresoTarea(set));
                    }
                }
            } catch (SQLException exception) {
                exception.printStackTrace();
            }
        }
    }

    public void tareaCreateProgress(String name) {
        try {
            Tarea tarea = RoleplayWebsocketsRoomsInfo.getTareasManager().getTarea(name);
            if (tarea != null) {
                try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                        PreparedStatement statement = connection.prepareStatement(
                                "INSERT INTO rp_pase_progresos (tarea_id, user_id, progress) VALUES (?, ?, ?)")) {
                    int cantidad = 1;
                    statement.setInt(1, tarea.getId());
                    statement.setInt(2, habbo.getHabboInfo().getId());
                    statement.setInt(3, cantidad);
                    statement.execute();
                } catch (SQLException e) {
                }
                this.update();
            }
        } catch (Exception e) {
        }
    }

    public void tareaUpdateProgress(String name, int cantidad) {

        try {
            Tarea tarea = RoleplayWebsocketsRoomsInfo.getTareasManager().getTarea(name);
            if (tarea != null) {

                ProgresoTarea progresoTarea = this.progresos.get(name);
                if (progresoTarea != null) {

                    try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                            PreparedStatement statement = connection.prepareStatement(
                                    "UPDATE rp_pase_progresos SET progress = ?, completed = ? WHERE user_id = ? AND tarea_id = ?")) {

                        int completed = 0;
                        if (cantidad >= tarea.getUsageAccount()) {
                            completed = 1;
                            progresoTarea.setCompleted(completed);
                        }

                        statement.setInt(1, cantidad);
                        statement.setInt(2, completed);
                        statement.setInt(3, habbo.getHabboInfo().getId());
                        statement.setInt(4, tarea.getId());
                        statement.execute();
                    } catch (SQLException e) {
                    }

                    this.update();
                }
            }
        } catch (Exception e) {
        }
    }

    public void insertProgress(String name) {
        try {

            Tarea tareaComprobate = RoleplayWebsocketsRoomsInfo.getTareasManager().getTarea(name);

            if (tareaComprobate != null) {

                ProgresoTarea progresoTarea = progresos.get(name);
                if (progresoTarea == null) {

                    // TutorialHabbo tutorialHabbo = new TutorialHabbo(habbo);
                    // if(tutorialHabbo.getTutorialActive() == true){
                    // return;
                    // }

                    HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

                    this.setNotification(this.getNotification() + 1);
                    this.tareaCreateProgress(name);
                    this.showButtonTareas();

                    if (tareaComprobate.getUsageAccount() == 1) {

                        habboRp.showMessage("/RoleplayWebsocketsRoomsInfo/tareas/quest.png",
                                RoleplayWebsocketsRoomsInfo.getTexts().getValue("rp_pase_tarea_quest_completed"));

                        habboRp.getHabboClient()
                                .send(new PlaySound("PlaySound", true, "success.mp3", habboRp.getHabbo()));

                        habboRp.getLevelHabbo().insertXp(tareaComprobate.getXp());

                        if (tareaComprobate.getCash() >= 1) {
                            habboRp.givePoints(tareaComprobate.getCash());
                        }

                        this.showSuccessTarea(tareaComprobate.getName());

                        this.update();
                    }

                    if (habboRp.getCacheString("windowTareasActive") != "none") {
                        if (habboRp.getCacheInt("windowTareasActive") == 1) {
                            this.ocultTareasWindow();
                            this.showTareasWindow();
                        }
                    }

                } else {
                    if (progresoTarea.getCompleted() == 0) {

                        HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

                        int cantidad = progresoTarea.getProgress() + 1;
                        if (cantidad > tareaComprobate.getUsageAccount()) {
                            return;
                        }

                        this.tareaUpdateProgress(name, cantidad);

                        if (cantidad >= tareaComprobate.getUsageAccount()) {

                            habboRp.showMessage("/RoleplayWebsocketsRoomsInfo/tareas/quest.png",
                                    RoleplayWebsocketsRoomsInfo.getTexts().getValue("rp_pase_tarea_quest_completed"));

                            habboRp.getHabboClient()
                                    .send(new PlaySound("PlaySound", true, "success.mp3", habboRp.getHabbo()));

                            habboRp.getLevelHabbo().insertXp(tareaComprobate.getXp());

                            if (tareaComprobate.getCash() >= 1) {
                                habboRp.givePoints(tareaComprobate.getCash());
                            }

                            this.showSuccessTarea(tareaComprobate.getName());

                        }

                        if (!tareaComprobate.getName().equalsIgnoreCase("JugarRP")) {
                            this.setNotification(this.getNotification() + 1);
                            this.showButtonTareas();
                        }

                        if (habboRp.getCacheString("windowTareasActive") != "none") {
                            if (habboRp.getCacheInt("windowTareasActive") == 1) {
                                this.ocultTareasWindow();
                                this.showTareasWindow();
                            }
                        }

                        this.update();
                    }
                }
            } else {
                RoleplayWebsocketsRoomsInfo.utilities().LOGGER_ERROR("La tarea " + name + " no se encuentra");
            }
        } catch (Exception e) {
        }
    }

    public void showSuccessTarea(String name) {

        try {

            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            JsonArray buttonInfo = new JsonArray();
            JsonObject jsonObject;

            Tarea tarea = RoleplayWebsocketsRoomsInfo.getTareasManager().getTarea(name);
            if (tarea != null) {
                jsonObject = new JsonObject();
                jsonObject.addProperty("title", tarea.getTitle());
                jsonObject.addProperty("descripcion", tarea.getDescripcion());
                jsonObject.addProperty("img", rutaImagesNotifications + tarea.getImagen());
                jsonObject.addProperty("eventOpen", "viewTareasWindow");
                buttonInfo.add(jsonObject);
            }

            habboRp.getHabboClient()
                    .send(new MessageManager("MessageManager", true, Outcoming.SuccessTarea, buttonInfo));
        } catch (Exception e) {
        }
    }

    public void showButtonTareas() {
        try {
            // HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            // JsonArray buttonInfo = new JsonArray();

            // JsonObject jsonObject;
            // jsonObject = new JsonObject();
            // jsonObject.addProperty("title", "Tareas roleplay");
            // jsonObject.addProperty("img", rutaImagesNotifications + "/tareas/quest.png");
            // jsonObject.addProperty("event", "viewTareasWindow");
            // jsonObject.addProperty("notification", notifications);
            // buttonInfo.add(jsonObject);

            // if (RoleplayWebsocketsRoomsInfo.getTutorialHabboRpManager().getButtonMoreTareas(habbo) != null) {
            //     buttonInfo.add(RoleplayWebsocketsRoomsInfo.getTutorialHabboRpManager().getButtonMoreTareas(habbo));
            // }

            // jsonObject = new JsonObject();
            // jsonObject.addProperty("title", "Empleos");
            // jsonObject.addProperty("img", rutaImagesNotifications + "/tareas/empleos.png");
            // jsonObject.addProperty("event", "viewEmpleosWindows");
            // buttonInfo.add(jsonObject);

            // habboRp.getHabboClient()
            //         .send(new MessageManager("MessageManager", true, Outcoming.TareasFranButton, buttonInfo));

        } catch (Exception e) {
        }
    }

    public void showTareasWindow() {
        try {

            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

            if (habboRp.getTareas().getNotification() >= 1) {
                habboRp.getTareas().setNotification(0);
                habboRp.getTareas().showButtonTareas();
            }

            JsonArray habboInfo = new JsonArray();
            JsonObject jsonObject;
            jsonObject = new JsonObject();
            String look = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.look.ruta")
                    + "&headonly=1&size=a&gesture=sml&head_direction=2";
            jsonObject.addProperty("look", look.replace("%LOOK%", habbo.getHabboInfo().getLook()));
            jsonObject.addProperty("username", habbo.getHabboInfo().getUsername());
            jsonObject.addProperty("user_id", habbo.getHabboInfo().getId());

            if (habboRp.getVip() == true) {
                jsonObject.addProperty("vip", 1);
            } else {
                jsonObject.addProperty("vip", 0);
            }

            jsonObject.addProperty("title_window",
                    RoleplayWebsocketsRoomsInfo.getTexts().getValue("rp_pase_title_window"));
            jsonObject.addProperty("title_part_1",
                    RoleplayWebsocketsRoomsInfo.getTexts().getValue("rp_pase_title_part_1"));
            jsonObject.addProperty("title_part_2",
                    RoleplayWebsocketsRoomsInfo.getTexts().getValue("rp_pase_title_part_2"));
            jsonObject.addProperty("title_part_3",
                    RoleplayWebsocketsRoomsInfo.getTexts().getValue("rp_pase_title_part_3"));
            jsonObject.addProperty("title_part_4",
                    RoleplayWebsocketsRoomsInfo.getTexts().getValue("rp_pase_title_part_4"));
            jsonObject.addProperty("title_part_5",
                    RoleplayWebsocketsRoomsInfo.getTexts().getValue("rp_pase_title_part_5"));

            Level level = RoleplayWebsocketsRoomsInfo.getLevelManagerRP().getLevel(habboRp.getLevelHabbo().getLevel());
            Level proximo_level = RoleplayWebsocketsRoomsInfo.getLevelManagerRP()
                    .getLevel(habboRp.getLevelHabbo().getLevel() + 1);
            jsonObject.addProperty("xp", habboRp.getLevelHabbo().getXP());
            jsonObject.addProperty("xp_required", proximo_level.getXP());
            jsonObject.addProperty("level", level.getLevel());
            habboInfo.add(jsonObject);

            habboRp.getHabboClient().send(new MessageManagerDoubleObjet("MessageManager", true,
                    Outcoming.TareasFranWindow, habboInfo, this.getObjetTareas()));

        } catch (Exception e) {

        }
    }

    public void ocultTareasWindow() {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            habboRp.getHabboClient().send(
                    new MessageManagerDoubleObjet("MessageManager", false, Outcoming.TareasFranWindow, null, null));
        } catch (Exception e) {
        }
    }

    public JsonObject getObjetTareas() {

        JsonObject Objeto = new JsonObject();
        JsonArray types = new JsonArray();

        habboRp.getLevelHabbo().loadPremios();
        habboRp.getLevelHabbo().loadPremiosVIP();

        int increment = 0;
        // Usando TreeMap para ordenar por clave de forma ascendente
        TreeMap<Integer, TareaType> sortedMapType = new TreeMap<>(
                RoleplayWebsocketsRoomsInfo.getTareasManager().getTypesTareas());
        for (TareaType typeTareas : sortedMapType.values()) {

            JsonArray arrayType = new JsonArray();
            JsonObject jsonObject = new JsonObject();

            JsonArray info = new JsonArray();
            JsonObject jsonObjectInfo = new JsonObject();
            jsonObjectInfo.addProperty("id", increment);
            jsonObjectInfo.addProperty("title", typeTareas.getTitle());
            jsonObjectInfo.addProperty("descripcion", typeTareas.getDescripcion());

            jsonObjectInfo.addProperty("imagen",
                    rutaImagesNotifications + typeTareas.getImagen());
            info.add(jsonObjectInfo);
            jsonObject.add("info", info);

            increment++;

            JsonArray tareas = new JsonArray();
            // Usando TreeMap para ordenar por clave de forma ascendente
            TreeMap<Integer, Tarea> sortedMapTareas = new TreeMap<>(typeTareas.getTareas());
            for (Tarea tarea : sortedMapTareas.values()) {

                JsonObject jsonObject2 = new JsonObject();
                jsonObject2.addProperty("title", tarea.getTitle());
                jsonObject2.addProperty("descripcion", tarea.getDescripcion());
                jsonObject2.addProperty("imagen",
                        rutaImagesNotifications + tarea.getImagen());
                jsonObject2.addProperty("xp", tarea.getXp());
                jsonObject2.addProperty("cash", tarea.getCash());
                jsonObject2.addProperty("orden", tarea.getOrden());
                jsonObject2.addProperty("required", tarea.getUsageAccount());

                ProgresoTarea progreso = this.progresos.get(tarea.getName());
                if (progreso != null) {
                    jsonObject2.addProperty("progress", progreso.getProgress());
                } else {
                    jsonObject2.addProperty("progress", 0);
                }

                tareas.add(jsonObject2);
            }

            jsonObject.add("tareas", tareas);
            arrayType.add(jsonObject);

            types.add(arrayType);
        }
        Objeto.add("types", types);

        TopNiveles topNiveles = new TopNiveles();
        topNiveles.load();
        JsonArray niveles = new JsonArray();
        String look = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.look.ruta")
                + "&headonly=1&size=a&gesture=sml&head_direction=2";
        for (TopUser topUser : topNiveles.getTopNiveles().values()) {
            JsonObject jsonObject2 = new JsonObject();
            jsonObject2.addProperty("look", look.replace("%LOOK%", topUser.getLook()));
            jsonObject2.addProperty("username", topUser.getUsername());
            jsonObject2.addProperty("user_id", topUser.getId());
            jsonObject2.addProperty("level", topUser.getLevel());
            jsonObject2.addProperty("xp", topUser.getXp());
            jsonObject2.addProperty("event", "OpenMenuLevelOtherTop");
            niveles.add(jsonObject2);
        }
        Objeto.add("niveles", niveles);

        JsonArray levels = new JsonArray();
        // Usando TreeMap para ordenar por clave de forma ascendente
        TreeMap<Integer, Level> sortedMapLevels = new TreeMap<>(
                RoleplayWebsocketsRoomsInfo.getLevelManagerRP().getLevels());
        for (Level level : sortedMapLevels.values()) {

            JsonArray arrayType = new JsonArray();
            JsonObject jsonObject = new JsonObject();

            JsonArray info = new JsonArray();
            JsonObject jsonObjectInfo = new JsonObject();
            jsonObjectInfo.addProperty("id", level.getLevel());
            jsonObjectInfo.addProperty("xp", level.getXP());

            if (habboRp.getLevelHabbo().comprobateNextLevel(level) == true) {
                jsonObjectInfo.addProperty("button_obtener", 1);
                jsonObjectInfo.addProperty("event_obtener", "OpenObtenerPremiosLevel");
                jsonObjectInfo.addProperty("imagen_fondo_cofre", global_url_images + "/media/content.png?v3");
            } else {
                jsonObjectInfo.addProperty("button_obtener", 0);
                jsonObjectInfo.addProperty("event_obtener", "none");

                if (habboRp.getLevelHabbo().getPremiosWon().get(level.getLevel()) != null) {
                    jsonObjectInfo.addProperty("imagen_fondo_cofre",
                            global_url_images + "/media/content_abierto.png?v3");
                } else {
                    jsonObjectInfo.addProperty("imagen_fondo_cofre", global_url_images + "/media/content.png?v3");
                }
            }

            if (habboRp.getLevelHabbo().comprobateNextLevelVip(level) == true) {
                jsonObjectInfo.addProperty("button_obtener_VIP", 1);
                jsonObjectInfo.addProperty("event_obtener_VIP", "OpenObtenerPremiosLevelVIP");
                jsonObjectInfo.addProperty("imagen_fondo_cofre_VIP",
                        global_url_images + "/media/content_VIP_abierto.png?v3");
            } else {
                jsonObjectInfo.addProperty("button_obtener_VIP", 0);
                jsonObjectInfo.addProperty("event_obtener_VIP", "none");
                if (habboRp.getLevelHabbo().getPremiosWonVIP().get(level.getLevel()) != null) {
                    jsonObjectInfo.addProperty("imagen_fondo_cofre_VIP",
                            global_url_images + "/media/content_VIP_abierto.png?v3");
                } else {
                    jsonObjectInfo.addProperty("imagen_fondo_cofre_VIP",
                            global_url_images + "/media/content_VIP.png?v3");
                }
            }

            if (habboRp.getLevelHabbo().getPremiosWon().get(level.getLevel()) == null) {
                jsonObjectInfo.addProperty("obtenido", 0);
                jsonObjectInfo.addProperty("img_caja",
                        global_url_images + "media/caja_cerrada3.png");
            } else {
                jsonObjectInfo.addProperty("obtenido", 1);
                jsonObjectInfo.addProperty("img_caja",
                        global_url_images + "media/caja_abierta2.png");
            }

            if (habboRp.getLevelHabbo().getPremiosWonVIP().get(level.getLevel()) == null) {
                jsonObjectInfo.addProperty("obtenidoVIP", 0);
                jsonObjectInfo.addProperty("img_cajaVIP",
                        global_url_images + "media/caja_cerrada3.png");
            } else {
                jsonObjectInfo.addProperty("obtenido", 1);
                jsonObjectInfo.addProperty("img_cajaVIP",
                        global_url_images + "media/caja_abierta2.png");
            }

            jsonObjectInfo.addProperty("img_vip",
                    global_url_images + "media/vip.png");

            info.add(jsonObjectInfo);
            jsonObject.add("info", info);

            JsonArray Rewards = new JsonArray();
            for (Reward reward : level.getRewards().values()) {
                JsonObject jsonObject2 = new JsonObject();
                jsonObject2.addProperty("name", reward.getName());
                jsonObject2.addProperty("title", reward.getTitle());
                jsonObject2.addProperty("descripcion", reward.getDescripcion());
                jsonObject2.addProperty("cantidad", reward.getCantidad());
                if (reward.getExist() == 1) {
                    jsonObject2.addProperty("imagen",
                            RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url.hotel.furnis")
                                    .replace("%url.hotel.base%", url_hotel_base)
                                    + "icons/" + reward.getName() + "_icon.png");
                } else {
                    jsonObject2.addProperty("imagen", global_url_images + "items/" + reward.getName() + ".png");
                }

                Rewards.add(jsonObject2);
            }
            jsonObject.add("rewards", Rewards);

            JsonArray RewardsVIP = new JsonArray();
            for (Reward reward : level.getRewardsVip().values()) {
                JsonObject jsonObject2 = new JsonObject();
                jsonObject2.addProperty("name", reward.getName());
                jsonObject2.addProperty("title", reward.getTitle());
                jsonObject2.addProperty("descripcion", reward.getDescripcion());
                jsonObject2.addProperty("cantidad", reward.getCantidad());
                if (reward.getExist() == 1) {
                    String global_url_hotel_furnis = RoleplayWebsocketsRoomsInfo.getConfig()
                            .getValue("global_url.hotel.furnis").replace("%url.hotel.base%", url_hotel_base);
                    jsonObject2.addProperty("imagen",
                            global_url_hotel_furnis + "icons/" + reward.getName() + "_icon.png");
                } else {
                    jsonObject2.addProperty("imagen",
                            global_url_images + "items/" + reward.getName() + ".png");
                }
                RewardsVIP.add(jsonObject2);
            }
            jsonObject.add("rewardsVIP", RewardsVIP);

            arrayType.add(jsonObject);

            levels.add(arrayType);
        }

        Objeto.add("levels", levels);

        return Objeto;
    }

}
