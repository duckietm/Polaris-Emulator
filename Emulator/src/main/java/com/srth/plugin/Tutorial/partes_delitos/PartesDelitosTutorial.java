package com.srth.plugin.Tutorial.partes_delitos;

import java.sql.SQLException;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Tutorial.HabboTutorial;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.websocket.Habbo.HabboClient;

import gnu.trove.map.hash.THashMap;

public class PartesDelitosTutorial {

        public PartesDelitosTutorial() {

        }

        public JsonArray getPartes(String type, Boolean forceVisible) {

                String url_hotel_base = RoleplayWebsocketsRoomsInfo.getConfig()
                                .getValue("global.url.hotel.base");
                String urlFrank = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url_images")
                                .replace("%url.hotel.base%", url_hotel_base) + "frank/";
                String urlBase = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url_images")
                                .replace("%url.hotel.base%", url_hotel_base);

                JsonArray jsonArray = new JsonArray();
                JsonObject jsonObject;

                String versionStr = type;
                switch (versionStr) {
                        case "2.1.0":
                                jsonObject = new JsonObject();
                                jsonObject.addProperty("url_image", urlBase);
                                jsonObject.addProperty("isMinimized", 0);
                                if (forceVisible) {
                                        jsonObject.addProperty("isMinimized", 0);
                                } else {
                                        jsonObject.addProperty("isMinimized", 1);
                                }
                                jsonObject.addProperty("img",
                                                urlFrank + "frank_04.gif");
                                jsonObject.addProperty("title", "¡Caete a llaverazos con un jugador y asesinalo!");
                                jsonObject.addProperty("desc",
                                                ""
                                                                + "Podrás hacerlo presionando al usuario objetivo lo que te mostrará su vida y los botones con acciones a realizar... Alli verás una llave, deberás presionarla para activar el modo PVP:<br><br>"
                                                                + "<div class='flex'> "
                                                                + "<section class='contentGif'>"
                                                                + "<b>¿Cómo hacerlo?</b><br>"
                                                                + "<img class='gif' src='tutorial/gif/eventManoPvpCombate.gif?v1' style='width: 350px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%);'/> <br>"
                                                                + "</section> "
                                                                + "</div>"
                                                                + "<span> <b>IMPORTANTE: </b> Debe ser de tarde o de noche, para poder activar el modo combate.</span>"
                                                                + "<br><br>¡Deberás asesinarlo para continuar! <br><br>"
                                                                + "");
                                jsonArray.add(jsonObject);

                                break;
                        case "2.2.0":
                                jsonObject = new JsonObject();
                                jsonObject.addProperty("event", "eventRecibirArmaIlegal");
                                jsonObject.addProperty("url_image", urlBase);
                                jsonObject.addProperty("isMinimized", 0);
                                if (forceVisible) {
                                        jsonObject.addProperty("isMinimized", 0);
                                } else {
                                        jsonObject.addProperty("isMinimized", 1);
                                }
                                jsonObject.addProperty("img",
                                                urlFrank + "frank_04.gif");
                                jsonObject.addProperty("title", "¡Recibe tu arma ilegal!");
                                jsonObject.addProperty("desc",
                                                ""
                                                                + "El arma ilegal te servira para dispararle a otros usuarios y asesinarlos:<br><br>"

                                                                + "<b>¿Cómo hacerlo?</b><br>"
                                                                + "<span>A continuación te entregaremos el arma ilegal, deberas buscarla en tu inventario y equiparla... Una vez la equipes luego presionarás en el botón de usar.</span>"

                                                                + "<br><br><span> <b>Continúa para obtener tu arma ilegal. </b><br>");
                                jsonArray.add(jsonObject);
                                break;
                        case "2.2.1":
                                jsonObject = new JsonObject();
                                jsonObject.addProperty("url_image", urlBase);
                                jsonObject.addProperty("isMinimized", 0);
                                if (forceVisible) {
                                        jsonObject.addProperty("isMinimized", 0);
                                } else {
                                        jsonObject.addProperty("isMinimized", 1);
                                }
                                jsonObject.addProperty("img",
                                                urlFrank + "frank_04.gif");
                                jsonObject.addProperty("title", "¡Activa el modo Shooter!");
                                jsonObject.addProperty("desc",
                                                ""
                                                                + "Para activar el modo shooter deberas equiparte tu arma del inventario y usarla:<br><br>"

                                                                + "<b>¿Cómo hacerlo?</b><br>"
                                                                + "<span>Busca tu arma ilegal en tu inventario y equiparla, luego presionar en usar.</span>"

                                                                + "<div class='flex'> "
                                                                + "<section class='contentGif'>"
                                                                + "<img class='gif' src='tutorial/gif/equiparArmaIlegalShooter.gif?v1' style='width: 350px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%);'/> <br>"
                                                                + "</section> "
                                                                + "</div>"
                                                                + "<span> <b>¡Equipala y usala para continuar!</b><br>");
                                jsonArray.add(jsonObject);
                                break;
                        case "2.2.2":
                                jsonObject = new JsonObject();
                                jsonObject.addProperty("url_image", urlBase);
                                jsonObject.addProperty("isMinimized", 0);
                                if (forceVisible) {
                                        jsonObject.addProperty("isMinimized", 0);
                                } else {
                                        jsonObject.addProperty("isMinimized", 1);
                                }
                                jsonObject.addProperty("img",
                                                urlFrank + "frank_04.gif");
                                jsonObject.addProperty("title", "¡Asesina a un jugador!");
                                jsonObject.addProperty("desc",
                                                ""
                                                                + "Para completar este reto, deberás asesinar a un jugador con tu arma:<br><br>"

                                                                + "<b>¿Cómo hacerlo?</b><br>"
                                                                + "<span>Una vez tengas equipada tu arma ilegal, podrás dar click sobre cualquier usuario para dispararle (deberás estar a una distancia prudente).</span>"

                                                                + "<div class='flex'> "
                                                                + "<section class='contentGif'>"
                                                                + "<img class='gif' src='tutorial/gif/asesinarShooterHabbo.gif?v1' style='width: 450px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%);'/> <br>"
                                                                + "</section> "
                                                                + "</div>"
                                                                + "<span> <b>¡Hazlo para continuar!</b><br>");
                                jsonArray.add(jsonObject);
                                break;
                        case "2.3.0":
                                jsonObject = new JsonObject();
                                jsonObject.addProperty("event", "eventRecibirLlaveCajeroAutomatico");
                                jsonObject.addProperty("url_image", urlBase);
                                jsonObject.addProperty("isMinimized", 0);
                                if (forceVisible) {
                                        jsonObject.addProperty("isMinimized", 0);
                                } else {
                                        jsonObject.addProperty("isMinimized", 1);
                                }
                                jsonObject.addProperty("img",
                                                urlFrank + "frank_04.gif");
                                jsonObject.addProperty("title", "¡Obten una llave para forzar cajeros!");
                                jsonObject.addProperty("desc",
                                                ""
                                                                + "<div class='flex' style='justify-content: center; align-items: center;'> "
                                                                + "<img class='gif' src='tutorial/png/cajero_furni.png?v1' style='width: 60px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%);'/>"
                                                                + "Te vamos a regalar una llave para que puedas forzar cajeros automáticos y sacarle la carga monetaria:<br><br>"
                                                                + "</div>"
                                                                + "<br><b>¿Cómo podre robar cajeros?</b><br>"
                                                                + "<span>Una vez recibas la llave, buscala en tu inventario, equipala y luego usala para colocartela en mano, podrás dar click sobre el cajero automático y te mostrará el boton de forzar cajero.</span>"
                                                                + "<br><br><br><span><b>¡Recibe tu llave ahora!</b></span>");
                                jsonArray.add(jsonObject);
                                break;
                        case "2.3.1":
                                jsonObject = new JsonObject();
                                jsonObject.addProperty("url_image", urlBase);
                                jsonObject.addProperty("isMinimized", 0);
                                if (forceVisible) {
                                        jsonObject.addProperty("isMinimized", 0);
                                } else {
                                        jsonObject.addProperty("isMinimized", 1);
                                }
                                jsonObject.addProperty("img",
                                                urlFrank + "frank_04.gif");
                                jsonObject.addProperty("title", "¡Roba el primer cajeros!");
                                jsonObject.addProperty("desc",
                                                ""
                                                                + "<br><b>¿Cómo robar cajeros?</b><br>"
                                                                + "<img class='gif' src='tutorial/png/robarCajero.png?v1' style='width: 300px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%);'/>"
                                                                + "<br><span>Una vez recibas la llave, buscala en tu inventario, equipala y luego usala para colocartela en mano, podrás dar click sobre el cajero automático y te mostrará el boton de forzar cajero.</span>"
                                                                + "<br><br><br><span><b>¡Robalo ahora!</b></span>");
                                jsonArray.add(jsonObject);
                                break;
                        default:
                                break;
                }

                return jsonArray;
        }

        public static void incomingListPartesDelitos(THashMap<String, Object> data, HabboClient wsclient)
                        throws SQLException {
                new IncomingListDelitos(data, wsclient);
        }

}