package com.srth.plugin.Tutorial.partes;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;

// Extiende AbstractParteTutorial
public class ParteTutorial16 extends AbstractParteTutorial {

        public ParteTutorial16() {
                // Llama al constructor de la clase base, pasando el número de parte 10.
                super(16);
        }

        /**
         * Implementación del método abstracto para definir el contenido de la Parte 10
         * (Comenzar a Cocinar - Repetido del 9).
         */
        @Override
        protected JsonArray crearContenidoJson() {
                JsonArray Array = new JsonArray();
                JsonObject jsonObject;
                String urlFrank = getUrlFrank(); // Usa el método de la clase base

                // PASO 1: Instrucción para usar
                jsonObject = new JsonObject();
                jsonObject.addProperty("url_image", getUrlBase());
                if (RoleplayWebsocketsRoomsInfo.getTutorialHabboRpManager().forceVisible) {
                        jsonObject.addProperty("isMinimized", 0);
                } else {
                        jsonObject.addProperty("isMinimized", 1);
                }
                jsonObject.addProperty("img", urlFrank + "frank_04.gif");
                jsonObject.addProperty("title", "Resguarda tu dinero");
                jsonObject.addProperty("desc",
                                "<span>¡Es hora de mantener seguro tu dinero! <b>Evita ser robado por los delincuentes</b>...</span><br><br>"
                                                + "<span><b>Presiona una o dos veces el cajero automático</b> para depositar tu dinero:</span>"
                                                + "<div class='flex'> "
                                                + "<section class='contentGif'>"
                                                + "<img class='gif' src='tutorial/png/objeto_cajero.png?v1' style='width: 60px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%); background: rgb(0 0 0 / 36%);'/>"
                                                + "</section> "
                                                + "</div>"
                                                + "<span>A continuación te mostramos como hacerlo...</span>");
                Array.add(jsonObject);

                // PASO 1: Instrucción para usar
                jsonObject = new JsonObject();
                jsonObject.addProperty("url_image", getUrlBase());
                if (RoleplayWebsocketsRoomsInfo.getTutorialHabboRpManager().forceVisible) {
                        jsonObject.addProperty("isMinimized", 0);
                } else {
                        jsonObject.addProperty("isMinimized", 1);
                }
                jsonObject.addProperty("img", urlFrank + "frank_04.gif");
                jsonObject.addProperty("title", "¿Cómo depositar tu dinero?");
                jsonObject.addProperty("desc",
                                "<span>Sigue estos pasos para depositar tu dinero</span>"
                                                + "<span></span>"
                                                + "<div class='flex'> "
                                                + "<section class='contentGif'>"
                                                + "<img class='gif' src='tutorial/gif/depositedDineroCajero.gif?v1' style='width: 350px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%); background: rgb(0 0 0 / 36%);'/>"
                                                + "</section> "
                                                + "</div>"
                                                + "<span>Una vez deposites tu dinero y consultes tu cuenta, <b>podrás alejarte del cajero para cerrar el menú</b>.</span> <br><br>  <span>Deposita tu dinero ahora...</span>");
                Array.add(jsonObject);

                return Array;
        }
}