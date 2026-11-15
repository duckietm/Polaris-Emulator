package com.srth.plugin.Horario;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Horario.Timer.TimerEstatus;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.websocket.communication.Outcoming;
import com.srth.plugin.websocket.communication.outgoing.messages.MessageManager;

import java.sql.SQLException;
import java.util.Collection;

public class HorarioManager {

    private String time_horario;
    private String estadoAnterior; // Variable para rastrear el estado previo
    private int segundosRestantes;
    private final int RP_CATEGORY_ID;
    public TimerEstatus timerEstatus; // Referencia al TimerEstatus

    public HorarioManager() {
        this.time_horario = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_rooms_fondo_timeHorario", "dia");
        this.estadoAnterior = this.time_horario;
        this.RP_CATEGORY_ID = RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.category.roleplay.id");

        this.segundosRestantes = obtenerDuracionSegundos(this.time_horario);

        this.timerEstatus = new TimerEstatus(this);
        Emulator.getThreading().run(this.timerEstatus);
    }

    public int obtenerDuracionSegundos(String estado) {
        switch (estado.toLowerCase()) {
            case "dia":
                return 10 * 60; // 600 segundos
            case "tarde":
                return 3 * 60; // 180 segundos
            case "noche":
                return 5 * 60; // 300 segundos
            default:
                return 10 * 60;
        }
    }

    public void comprobateStatus() throws SQLException {
        // Guardamos el estado actual como anterior antes de cambiarlo
        this.estadoAnterior = this.time_horario;

        switch (this.time_horario.toLowerCase()) {
            case "dia":
                this.time_horario = "tarde";
                break;
            case "tarde":
                this.time_horario = "noche";
                break;
            default:
                this.time_horario = "dia";
                break;
        }

        this.segundosRestantes = obtenerDuracionSegundos(this.time_horario);

        RoleplayWebsocketsRoomsInfo.getConfig().updateData("global_rooms_fondo_timeHorario", this.time_horario);
        this.changeBackgroundTimer();
    }

    public void changeBackgroundTimer() throws SQLException {
        String fondoColor = RoleplayWebsocketsRoomsInfo.getConfig()
                .getValue("global_rooms_fondo_timeHorario_color_" + time_horario);

        String textKey = null;
        if (time_horario.equalsIgnoreCase("noche")) {
            textKey = "action_horario_nocturo_delite_avise";
        } else if (time_horario.equalsIgnoreCase("tarde")) {
            textKey = "action_horario_tarde_delite_avise";
        }

        String whisperMsg = (textKey != null) ? RoleplayWebsocketsRoomsInfo.getTexts().getValue(textKey) : null;
        MessageManager timeMessage = createTimeMessage(true);

        Collection<Room> activeRooms = Emulator.getGameEnvironment().getRoomManager().getActiveRooms();
        for (Room room : activeRooms) {
            if (room.getCategory() != RP_CATEGORY_ID)
                continue;

            for (HabboItem item : room.getFloorItems()) {
                if (item.getBaseItem().getName().equalsIgnoreCase("roombg_color")) {
                    item.setZ(99.9);
                    item.setExtradata(fondoColor);
                    room.updateItem(item);
                }
            }

            for (Habbo habbo : room.getHabbos()) {
                if (whisperMsg != null) {
                    habbo.whisper(whisperMsg);
                }

                try {
                    HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
                    if (habboRp != null && habboRp.getHabboClient() != null) {
                        habboRp.getHabboClient().send(timeMessage);
                    }
                } catch (Exception e) {
                }
            }
        }

        RoleplayWebsocketsRoomsInfo.UpdateAllRoomsContentItem("roombg_color", fondoColor, 99.9);
    }

    public void showDiaTardeNoche(Habbo habbo) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            if (habboRp != null && habboRp.getHabboClient() != null) {
                habboRp.getHabboClient().send(createTimeMessage(true));
            }
        } catch (Exception e) {
        }
    }

    public void ocultDiaTardeNoche(Habbo habbo) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            if (habboRp != null && habboRp.getHabboClient() != null) {
                habboRp.getHabboClient()
                        .send(new MessageManager("MessageManager", false, Outcoming.DiaTardeNoche, null));
            }
        } catch (Exception e) {
        }
    }

    private MessageManager createTimeMessage(boolean visible) throws SQLException {
        String urlBase = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.url.hotel.base");
        String imgUrl = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url.hotel.notifications")
                .replace("%url.hotel.base%", urlBase) + "RoleplayWebsocketsRoomsInfo/horarios/" + this.time_horario
                + ".png";

        JsonObject jsonObjectInfo = new JsonObject();
        jsonObjectInfo.addProperty("img", imgUrl);
        jsonObjectInfo.addProperty("turno", this.time_horario);
        jsonObjectInfo.addProperty("segundos", this.segundosRestantes);

        JsonArray listInfo = new JsonArray();
        listInfo.add(jsonObjectInfo);

        return new MessageManager("MessageManager", visible, Outcoming.DiaTardeNoche, listInfo);
    }

    public String getTimeHorario() {
        return this.time_horario;
    }

    public String getEstadoAnterior() {
        return this.estadoAnterior;
    }

    public int getSegundosRestantes() {
        return this.segundosRestantes;
    }

    public void setSegundosRestantes(int segundosRestantes) {
        this.segundosRestantes = segundosRestantes;
    }
}