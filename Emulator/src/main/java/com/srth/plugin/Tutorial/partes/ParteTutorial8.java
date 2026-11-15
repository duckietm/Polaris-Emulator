package com.srth.plugin.Tutorial.partes;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;

// Extiende AbstractParteTutorial
public class ParteTutorial8 extends AbstractParteTutorial {

        public ParteTutorial8() {
                // Llama al constructor de la clase base, pasando el número de parte 8.
                super(8);
        }

        /**
         * Implementación del método abstracto para definir el contenido de la Parte 8
         * (Usar la Nevera/Refrigerador).
         */
        @Override
        protected JsonArray crearContenidoJson() {
                JsonArray Array = new JsonArray();
                JsonObject jsonObject;
                String urlFrank = getUrlFrank(); // Usa el método de la clase base

                // PASO 1: Usar la Nevera para buscar ingredientes
                jsonObject = new JsonObject();
                jsonObject.addProperty("url_image", getUrlBase());
                if (RoleplayWebsocketsRoomsInfo.getTutorialHabboRpManager().forceVisible) {
                        jsonObject.addProperty("isMinimized", 0);
                } else {
                        jsonObject.addProperty("isMinimized", 1);
                }
                jsonObject.addProperty("img", urlFrank + "frank_04.gif");
                jsonObject.addProperty("title", "Busca los ingredientes");
                jsonObject.addProperty("desc",
                                "Acercate a tu nevera y presionala dos veces para buscar los ingredientes necesarios para cocinar: <br>"
                                                + "<div class='flex'> "
                                                + "<section class='contentGif'>"
                                                + "<img class='gif' src='tutorial/png/objeto_nevera.png?v1' style='width: 100px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%); background: rgb(0 0 0 / 36%);'/> "
                                                + "</section> "
                                                + "</div>");
                // Asumiendo que el tutorial avanza con la interacción (doble clic)
                Array.add(jsonObject);

                return Array;
        }
}