package com.srth.plugin.Tutorial.partes;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;

// Extiende AbstractParteTutorial
public class ParteTutorial15 extends AbstractParteTutorial {

        public ParteTutorial15() {
                // Llama al constructor de la clase base, pasando el número de parte 10.
                super(15);
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

                // PASO 1: Instrucción para usar la Cocina
                jsonObject = new JsonObject();
                jsonObject.addProperty("url_image", getUrlBase());
                if (RoleplayWebsocketsRoomsInfo.getTutorialHabboRpManager().forceVisible) {
                        jsonObject.addProperty("isMinimized", 0);
                } else {
                        jsonObject.addProperty("isMinimized", 1);
                }
                jsonObject.addProperty("img", urlFrank + "frank_04.gif");
                jsonObject.addProperty("title", "Regresemos a la ciudad");
                jsonObject.addProperty("desc",
                                "<span>Ya que tienes comida en tu inventario, <b>regresemos al hospital</b>...</span><br>"
                                                + "<span>Una forma de navegar por las salas del modo roleplay es a través del mapa...</span>"
                                                + "<div class='flex'> "
                                                + "<section class='contentGif'>"
                                                + "<img class='gif' src='tutorial/png/mapa1.png?v1' style='width: 60px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%); background: rgb(0 0 0 / 36%);'/>"
                                                + "</section> "
                                                + "</div>"
                                                + "<span>Te mostraremos el mapa a continuación para que veas las salas disponibles y regreses al hospital...</span>");
                jsonObject.addProperty("event", "showMapaRoleplayEvent");
                Array.add(jsonObject);

                return Array;
        }
}