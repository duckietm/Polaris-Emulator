package com.srth.plugin.habbo.Frank.Frank.Flechas;

import com.eu.habbo.habbohotel.users.Habbo;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.websocket.communication.Outcoming;
import com.srth.plugin.websocket.communication.outgoing.messages.MessageManager;

public class FlechasIndicadoras {

    String url_hotel_base = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.url.hotel.base");
    String global_url_images = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url_images")
            .replace("%url.hotel.base%", url_hotel_base);

    public FlechasIndicadoras() {

    }

    public void showFlecha(Habbo habbo, int number) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

            JsonArray Flecha = new JsonArray();
            JsonObject jsonObject2;

            // Mostrar flecha para ver
            jsonObject2 = new JsonObject();
            jsonObject2.addProperty("clase", "position" + number);
            jsonObject2.addProperty("image",
                    global_url_images + "/mano.gif");
            Flecha.add(jsonObject2);

            habboRp.getHabboClient().send(new MessageManager("MessageManager", true, Outcoming.FlechaTutorial, Flecha));

        } catch (Exception e) {
        }
    }

    public void ocultFlecha(Habbo habbo) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

            habboRp.getHabboClient().send(new MessageManager("MessageManager", false, Outcoming.FlechaTutorial, null));
        } catch (Exception e) {
        }
    }
}
