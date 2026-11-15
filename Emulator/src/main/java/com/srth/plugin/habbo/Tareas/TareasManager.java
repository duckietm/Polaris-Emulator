package com.srth.plugin.habbo.Tareas;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.eu.habbo.Emulator;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.Tareas.Level.Level;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.communication.Outcoming;
import com.srth.plugin.websocket.communication.outgoing.messages.MessageManager;

import gnu.trove.map.hash.THashMap;

public class TareasManager {

    THashMap<Integer, TareaType> typesTareas = new THashMap<Integer, TareaType>();
    THashMap<String, Tarea> tareas = new THashMap<String, Tarea>();

    public TareasManager() {
    }

    public void update() throws SQLException {
        this.typesTareas.clear();
        this.loadTypes();
    }

    public void loadTypes() throws SQLException {
        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection()) {
            try (PreparedStatement preparedStatement = conn
                    .prepareStatement("SELECT * FROM rp_pase_tareas_types ORDER BY id ASC")) {
                ResultSet set = preparedStatement.executeQuery();
                while (set.next()) {

                    TareaType newTareaType = new TareaType(set);
                    typesTareas.put(set.getInt("id"), newTareaType);

                    try (Connection connTarea = Emulator.getDatabase().getDataSource().getConnection()) {
                        try (PreparedStatement preparedStatementTarea = conn.prepareStatement(
                                "SELECT * FROM rp_pase_tareas WHERE `id_type`= ? ORDER BY `orden` ASC")) {
                            preparedStatementTarea.setInt(1, set.getInt("id"));
                            ResultSet setTarea = preparedStatementTarea.executeQuery();
                            while (setTarea.next()) {

                                Tarea newTarea = newTareaType.createTarea(setTarea);

                                tareas.put(setTarea.getString("name"), newTarea);
                            }
                        } catch (SQLException exception) {
                            exception.printStackTrace();
                        }
                    }

                }
            } catch (SQLException exception) {
                exception.printStackTrace();
            }
        }
    }

    public static JsonArray sortJsonArrayInPlaceAscending(JsonArray jsonArray, String key) {
        List<JsonElement> jsonList = new ArrayList<>();
        for (int i = 0; i < jsonArray.size(); i++) {
            jsonList.add(jsonArray.get(i));
        }

        jsonList.sort(Comparator.comparingInt(o -> o.getAsJsonObject().get(key).getAsInt()));

        // Limpiar el JsonArray original
        while (jsonArray.size() > 0) {
            jsonArray.remove(0);
        }

        // Agregar los elementos ordenados de nuevo al JsonArray original
        for (JsonElement element : jsonList) {
            jsonArray.add(element);
        }

        return jsonArray;
    }

    public THashMap<String, Tarea> getTareas() {
        return tareas;
    }

    public Tarea getTarea(String name) {
        Tarea tareaSet = this.getTareas().get(name);
        return tareaSet;
    }

    public Tarea getTareaById(int id) {
        Tarea getTarea = null;
        for (Tarea tareas : this.getTareas().values()) {
            if (tareas.getId() == id) {
                getTarea = tareas;
            }
        }
        return getTarea;
    }

    public THashMap<Integer, TareaType> getTypesTareas() {
        return typesTareas;
    }

    public void IncomingListActions(THashMap<String, Object> data, HabboClient wsclient) {
        try {
            if (wsclient != null) {

                String name = (String) data.get("name");

                if (name.equalsIgnoreCase("closeTareasWindow")) {

                    HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(wsclient.getHabbo());
                    habboRp.removeCache("windowTareasActive");

                } else if (name.equalsIgnoreCase("viewTareasWindow")) {

                    HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(wsclient.getHabbo());
                    habboRp.getTareas().showTareasWindow();

                    habboRp.registerCache("windowTareasActive", "1");
                } else if (name.equalsIgnoreCase("OpenMenuLevelOtherTop")) {

                    int user_id = (int) ((double) data.get("action"));

                    HabboRp habboRp_other = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRpOffline(user_id);
                    HabboRp habboRp_my = RoleplayWebsocketsRoomsInfo.getHabboRpManager()
                            .getHabboRp(wsclient.getHabbo());

                    habboRp_my.getHabboClient().send(new MessageManager("MessageManager", true,
                            Outcoming.ViewLevelOtherHabboWindow, habboRp_other.getLevelHabbo().getArrayOffline()));

                } else if (name.equalsIgnoreCase("OpenObtenerPremiosLevel")) {

                    int level_id = (int) ((double) data.get("action"));
                    Level level = RoleplayWebsocketsRoomsInfo.getLevelManagerRP().getLevel(level_id);

                    HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(wsclient.getHabbo());
                    habboRp.getLevelHabbo().obtenerRewards(level, false);

                } else if (name.equalsIgnoreCase("OpenObtenerPremiosLevelVIP")) {

                    int level_id = (int) ((double) data.get("action"));
                    Level level = RoleplayWebsocketsRoomsInfo.getLevelManagerRP().getLevel(level_id);

                    HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(wsclient.getHabbo());
                    habboRp.getLevelHabbo().obtenerRewards(level, true);
                }
            }
        } catch (Exception e) {
        }
    }
}
