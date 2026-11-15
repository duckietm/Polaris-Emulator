package com.srth.plugin.websocket.communication.incoming.messages.fetch;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.Jobs.Job;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.Habbo.HabboClientManager;
import com.srth.plugin.websocket.communication.incoming.IncomingMessage;
import gnu.trove.map.hash.THashMap;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import java.nio.charset.StandardCharsets;

public class GetJobsWindowsDataRequest extends IncomingMessage {

    public GetJobsWindowsDataRequest(String header, THashMap<String, Object> data, Channel client) {
        super(header, data, client);
        this.execute();
    }

    @Override
    public void execute() {
        try {
            HabboClient clientws = HabboClientManager.getHabboClient(this.client);
            if (clientws == null) {
                return;
            }
            
            Habbo habbo = clientws.getHabbo();

            // 1. Preparar el objeto de respuesta principal
            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("header", "JobsDataResponse");

            JsonArray dataArray = new JsonArray();
            JsonObject userData = new JsonObject();

            // Datos básicos del usuario
            userData.addProperty("username", habbo.getHabboInfo().getUsername());
            userData.addProperty("id", habbo.getHabboInfo().getId());

            // 2. Preparar el Array de trabajos disponibles
            JsonArray jobsDisponiblesArray = new JsonArray();

            String url_hotel_base = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.url.hotel.base");
            String urlNotifications = RoleplayWebsocketsRoomsInfo.getConfig()
                    .getValue("global_url.hotel.notifications")
                    .replace("%url.hotel.base%", url_hotel_base);

            // 3. Recorrer y llenar el array de empleos
            for (Job job : RoleplayWebsocketsRoomsInfo.getJobsManager().getJobs().values()) {
                JsonObject jobItem = new JsonObject();
                jobItem.addProperty("jobName", job.getName());
                jobItem.addProperty("jobTimeTurnos", job.getTimeTurnoInSeconds());
                jobItem.addProperty("jobSueldoTurnos", job.getSueldoTurno());
                jobItem.addProperty("imagen", urlNotifications + "RoleplayWebsocketsRoomsInfo/turnos/job_"
                        + job.getName().toLowerCase() + ".png");
                jobItem.addProperty("descripcion", "¡Tu próxima paga por cumplir un turno como " + job.getName() + "!");

                // --- NUEVA SECCIÓN: Ranking de Turnos ---
                JsonArray mostTurnosArray = new JsonArray();

                // Obtenemos los 5 mejores (userId -> turnos)
                job.getMostTurnos().forEach((userId, totalTurnos) -> {
                    JsonObject rankItem = new JsonObject();

                    // Intentamos obtener el nombre del usuario desde el gestor de Habbos del
                    // emulador
                    HabboInfo habboEmpleado = HabboManager.getOfflineHabboInfo(userId);
                    String nombre = (habboEmpleado != null) ? habboEmpleado.getUsername() : "Usuario " + userId;
                    String look = (habboEmpleado != null) ? habboEmpleado.getLook() : "Look";

                    rankItem.addProperty("userId", userId);
                    rankItem.addProperty("userName", nombre);
                    rankItem.addProperty("userLook",
                            RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.look.ruta").replace("%LOOK%",
                                    look + "")
                                    + "&headonly=1&size=a&gesture=sml&direction=2&head_direction=3");
                    rankItem.addProperty("totalTurnos", totalTurnos);

                    mostTurnosArray.add(rankItem);
                });

                // Añadimos el ranking al objeto del empleo
                jobItem.add("rankingTurnos", mostTurnosArray);
                // ----------------------------------------

                jobsDisponiblesArray.add(jobItem);
            }

            // 4. Insertar el array de trabajos dentro del objeto de usuario
            userData.add("jobsDisponibles", jobsDisponiblesArray);

            // 5. Meter el userData en el data array principal
            dataArray.add(userData);
            responseJson.add("data", dataArray);

            // Envío del paquete
            byte[] responseBytes = responseJson.toString().getBytes(StandardCharsets.UTF_8);
            this.client.writeAndFlush(new BinaryWebSocketFrame(
                    this.client.alloc().buffer().writeBytes(responseBytes)));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}