package com.srth.plugin.websocket.communication.incoming.messages.fetch;

import com.eu.habbo.habbohotel.users.Habbo;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Turnos.HabboTurnos;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.Jobs.Job;
import com.srth.plugin.habbo.Jobs.JobEmpleado;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.Habbo.HabboClientManager;
import com.srth.plugin.websocket.communication.incoming.IncomingMessage;
import gnu.trove.map.hash.THashMap;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;

import java.nio.charset.StandardCharsets;

public class GetTurnosJobDataRequest extends IncomingMessage {

    // Constante de tiempo inicial (10 minutos = 600 segundos)
    private static final int INITIAL_TURN_TIME = 600;

    public GetTurnosJobDataRequest(String header, THashMap<String, Object> data, Channel client) {
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
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            Job jobActive = habboRp.getJobActive();

            JsonObject responseJson = new JsonObject();
            responseJson.addProperty("header", "TurnosJobDataResponse");

            JsonArray dataArray = new JsonArray();
            JsonObject userData = new JsonObject();

            userData.addProperty("username", habbo.getHabboInfo().getUsername());
            userData.addProperty("id", habbo.getHabboInfo().getId());

            // Creamos el objeto para los datos del trabajo
            JsonObject jobData = new JsonObject();

            if (jobActive != null) {
                JobEmpleado jobEmpleado = jobActive.getEmpleado(habbo);

                // Inicialización de tiempo si es necesario
                if (jobEmpleado.getJobTimeRemaining() <= 0) {
                    jobEmpleado.setJobTimeRemaining(INITIAL_TURN_TIME);
                }

                String url_hotel_base = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.url.hotel.base");
                String urlNotifications = RoleplayWebsocketsRoomsInfo.getConfig()
                        .getValue("global_url.hotel.notifications")
                        .replace("%url.hotel.base%", url_hotel_base);

                userData.addProperty("hasJob", true);
                jobData.addProperty("jobName", jobActive.getName()); // Nombre del empleo
                jobData.addProperty("remainingTime", jobEmpleado.getJobTimeRemaining());
                jobData.addProperty("imagen", urlNotifications + "RoleplayWebsocketsRoomsInfo/turnos/job_"
                        + habboRp.getJobActive().getName() + ".png");
                jobData.addProperty("descripcion", "¡Tu proxíma paga por cumplir un turno como empleado de "
                        + habboRp.getJobActive().getName() + "!");
            }

            // Añadimos el objeto de trabajo al usuario
            userData.add("jobInfo", jobData);

            HabboTurnos habboTurnos = RoleplayWebsocketsRoomsInfo.getTurnosManager().getHabboTurnos(habbo);
            if (habboTurnos != null) {

                // Añadimos el objeto de otros turnos
                userData.add("turnInfo", habboTurnos.getTurnosObjet());
            }

            dataArray.add(userData);
            responseJson.add("data", dataArray);

            byte[] responseBytes = responseJson.toString().getBytes(StandardCharsets.UTF_8);
            this.client.writeAndFlush(new BinaryWebSocketFrame(
                    this.client.alloc().buffer().writeBytes(responseBytes)));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}