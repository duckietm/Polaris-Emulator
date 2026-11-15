package com.srth.plugin.Tutorial.partes;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;

// Extiende AbstractParteTutorial
public class ParteTutorial9 extends AbstractParteTutorial {

        public ParteTutorial9() {
                // Llama al constructor de la clase base, pasando el número de parte 9.
                super(9);
        }

        /**
         * Implementación del método abstracto para definir el contenido de la Parte 9
         * (Comenzar a Cocinar).
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
                jsonObject.addProperty("title", "Comienza a cocinar");
                jsonObject.addProperty("desc",
                                "Acercate a tu cocina y presionala dos veces para comenzar a cocinar: <br>"
                                                + "<div class='flex'> "
                                                + "<section class='contentGif'>"
                                                + "<img class='gif' src='tutorial/png/objeto_cocina.png?v1' style='width: 100px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%); background: rgb(0 0 0 / 36%);'/> "
                                                + "</section> "
                                                + "</div>");
                // No se incluye el evento aquí, ya que el tutorial avanza al interactuar con el
                // objeto.
                Array.add(jsonObject);

                return Array;
        }
}