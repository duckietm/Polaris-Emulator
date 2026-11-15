package com.srth.plugin.websocket.communication.incoming.messages.fetch;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Crafteos.CrafteosTypes;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.Jobs.Job;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.Habbo.HabboClientManager;
import com.srth.plugin.websocket.communication.incoming.IncomingMessage;
import gnu.trove.map.hash.THashMap;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import java.nio.charset.StandardCharsets;

public class GetShopBotsDataRequest extends IncomingMessage {

    public GetShopBotsDataRequest(String header, THashMap<String, Object> data, Channel client) {
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
            responseJson.addProperty("header", "ShopBotsDataResponse");

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

            // 4. Insertar el array de trabajos dentro del objeto de usuario
            userData.add("types",
                    RoleplayWebsocketsRoomsInfo.getShopBotsManager().getData(habbo, "carpinteros"));

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