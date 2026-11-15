package com.srth.plugin.Tutorial.partes_informal;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;

public class PartesInformalArrays {

        public PartesInformalArrays() {

        }

        public JsonArray getPartes(int type) {

                String url_hotel_base = RoleplayWebsocketsRoomsInfo.getConfig()
                                .getValue("global.url.hotel.base");
                String urlFrank = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url_images")
                                .replace("%url.hotel.base%", url_hotel_base) + "frank/";
                String urlBase = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url_images")
                                .replace("%url.hotel.base%", url_hotel_base);

                JsonArray jsonArray = new JsonArray();
                JsonObject jsonObject;
                switch (type) {

                        case 0:
                                jsonObject = new JsonObject();
                                jsonObject.addProperty("url_image", urlBase);
                                jsonObject.addProperty("informal", true);
                                jsonObject.addProperty("img",
                                                urlFrank + "frank_04.gif");
                                jsonObject.addProperty("title", "¡Lago de la ciudad!");
                                jsonObject.addProperty("desc",
                                                "Este es el lago de la ciudad. <br><br>"
                                                                +
                                                                "Acá podrás pasar tus tiempos libres pescando y recolectándo peces para venderlos al bot pescador de la sala, o a otros usuarios del hotel.");
                                jsonArray.add(jsonObject);
                                break;
                        case 1:
                                jsonObject = new JsonObject();
                                jsonObject.addProperty("url_image", urlBase);
                                jsonObject.addProperty("informal", true);
                                jsonObject.addProperty("img",
                                                urlFrank + "frank_04.gif");
                                jsonObject.addProperty("title", "¡Calles de la ciudad!");
                                jsonObject.addProperty("desc",
                                                "¡Estás viendo  las calles de la ciudad! <br><br>"
                                                                +
                                                                "Estas calles suelen ser peligrosas cuando es de noche, es por ello que en breve crearemos una casa para que estes seguro cuando sea de noche. <br><br>"
                                                                +
                                                                "Tambien puedes refugiarte en el hospital del hotel.");
                                jsonArray.add(jsonObject);
                                break;
                        default:
                                break;
                }

                return jsonArray;
        }

}
