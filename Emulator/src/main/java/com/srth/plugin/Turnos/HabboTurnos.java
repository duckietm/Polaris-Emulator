package com.srth.plugin.Turnos;

import java.util.concurrent.ConcurrentHashMap;
import com.eu.habbo.habbohotel.users.Habbo;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;

public class HabboTurnos {

    private Habbo habbo;
    private final ConcurrentHashMap<String, TurnoObjet> turnosList = new ConcurrentHashMap<>();

    public HabboTurnos(Habbo habbo) {
        this.habbo = habbo;
    }

    public ConcurrentHashMap<String, TurnoObjet> getTurnosList() {
        return turnosList;
    }

    public void removeTurno(String name) {
        this.turnosList.remove(name);
    }

    public void registerTurno(String name, int time, String desc, int reward, String imagen) {
        TurnoObjet newTurnoObjet = new TurnoObjet(time, name, desc, reward, imagen);
        this.turnosList.put(name, newTurnoObjet);
    }

    public JsonObject getTurnosObjet() {

        String url_hotel_base = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.url.hotel.base");
        String urlNotifications = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url.hotel.notifications")
                .replace("%url.hotel.base%", url_hotel_base);

        // Objeto contenedor principal
        JsonObject response = new JsonObject();

        // Lista donde agruparemos los distintos turnos
        JsonArray turnosArray = new JsonArray();

        for (TurnoObjet jsonElement : this.turnosList.values()) {
            // Creamos un objeto individual para CADA tarea/turno
            JsonObject turnData = new JsonObject();

            turnData.addProperty("jobName", jsonElement.getName());
            turnData.addProperty("remainingTime", jsonElement.getTime());
            turnData.addProperty("imagen", urlNotifications + "RoleplayWebsocketsRoomsInfo/" + jsonElement.getImagen());
            turnData.addProperty("descripcion", jsonElement.getDesc());

            // Lo añadimos al array
            turnosArray.add(turnData);
        }

        // Añadimos la lista completa de turnos al objeto de respuesta
        response.add("turnos", turnosArray);

        return response;
    }

}