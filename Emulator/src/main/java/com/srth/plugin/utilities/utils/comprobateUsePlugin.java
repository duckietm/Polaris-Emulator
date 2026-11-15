package com.srth.plugin.utilities.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import com.eu.habbo.Emulator;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;

import gnu.trove.map.hash.THashMap;

public class comprobateUsePlugin {

    public static List<JsonObject> listaHotelesPermitidos = new ArrayList<>();
    public static THashMap<String, String> listaPermitida = new THashMap<String, String>();

    public void loadHotelesPermited() {

        try {

            String url = Emulator.getConfig().getValue("camera.url");
            int terceraBarra = url.indexOf("/", url.indexOf("/", url.indexOf("/") + 1) + 1);
            String linkCameraHotel = "";
            if (terceraBarra != -1) {
                linkCameraHotel = url.substring(0, url.indexOf("/", url.indexOf("/", url.indexOf("/") + 1) + 1));
            } else {
                int segundaBarra = url.indexOf("/", url.indexOf("/") + 1);
                if (segundaBarra != -1) {
                    linkCameraHotel = url.substring(0, segundaBarra);
                }
            }

            if (linkCameraHotel.equalsIgnoreCase("http://localhost")) {
                listaPermitida.put(linkCameraHotel, linkCameraHotel);
                return;
            }

            URL urlObj = new URL("https://srthroneyt.github.io/roleplay_directorios/hoteles.json");
            HttpURLConnection connection = (HttpURLConnection) urlObj.openConnection();
            connection.setRequestMethod("GET");
            connection.connect();
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("Error al obtener datos de la URL. Código de respuesta: " + responseCode);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String line;
            StringBuilder response = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            connection.disconnect();
            String jsonResponse = response.toString();
            JsonElement jsonElement = JsonParser.parseString(jsonResponse);
            if (jsonElement.isJsonArray()) {
                JsonArray jsonArray = jsonElement.getAsJsonArray();

                for (JsonElement element : jsonArray) {
                    JsonObject hotelesJson = element.getAsJsonObject();
                    listaHotelesPermitidos.add(hotelesJson);
                }

                for (JsonObject hotelesJson : listaHotelesPermitidos) {
                    if (hotelesJson.get("url").getAsString().equalsIgnoreCase(linkCameraHotel)) {
                        listaPermitida.put(linkCameraHotel, linkCameraHotel);
                    }
                }
            }
        } catch (IOException error) {
            System.err.println("Error al obtener o procesar los datos: " + error.getMessage());
        }

    }

    public static Boolean execute() {

        Boolean result = false;

        String url = Emulator.getConfig().getValue("camera.url");
        int terceraBarra = url.indexOf("/", url.indexOf("/", url.indexOf("/") + 1) + 1);
        String linkCameraHotel = "";
        if (terceraBarra != -1) {
            linkCameraHotel = url.substring(0, url.indexOf("/", url.indexOf("/", url.indexOf("/") + 1) + 1));
        } else {
            int segundaBarra = url.indexOf("/", url.indexOf("/") + 1);
            if (segundaBarra != -1) {
                linkCameraHotel = url.substring(0, segundaBarra);
            }
        }

        if (listaPermitida.size() == 0) {
            RoleplayWebsocketsRoomsInfo.utilities().LOGGER("\r\n");
            RoleplayWebsocketsRoomsInfo.utilities().LOGGER_ERROR("[ROLEPLAY WEBSOCKETS ROOM INFO] | TU HOTEL: "
                    + linkCameraHotel + " NO TIENE PERMITIDO EL USO DEL PLUGIN.");
            RoleplayWebsocketsRoomsInfo.utilities().LOGGER_ERROR(
                    "[ROLEPLAY WEBSOCKETS ROOM INFO] | El plugin RoleplayWebsocketsRoomsInfo-1.0 fue desactivado debido a que es ilegal");
            RoleplayWebsocketsRoomsInfo.utilities().LOGGER_ERROR(
                    "[ROLEPLAY WEBSOCKETS ROOM INFO] | Muchas gracias por permitir la prueba, debera eliminar el plugin de la carpeta de plugins para continuar...");
            RoleplayWebsocketsRoomsInfo.utilities().LOGGER_ERROR(
                    "[ROLEPLAY WEBSOCKETS ROOM INFO] | Contacta con el creador en caso de interesarle (Discord: SrThrone#5868)");
            RoleplayWebsocketsRoomsInfo.utilities().LOGGER("\r\n");
            new disablePlugin().execute();
        }

        if (listaPermitida.get(linkCameraHotel) != null) {
            return result = true;
        }

        return result;
    }

    public static void comprobateLoad() {
        if (execute() == false) {
            Emulator.prepareShutdown();
            Emulator.isShuttingDown = true;
            Emulator.isReady = false;
        } else {
            RoleplayWebsocketsRoomsInfo.utilities().LOGGER("[ROLEPLAY WEBSOCKETS ROOM INFO] | CARGADO EXITOSAMENTE!");
        }
    }

    public static void comprobateShutdown() {
        if (execute() == false) {
            Emulator.prepareShutdown();
            Emulator.isShuttingDown = true;
            Emulator.isReady = false;
        }
    }
}
