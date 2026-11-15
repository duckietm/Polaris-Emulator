package com.srth.plugin.Tutorial.partes;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;

// Extiende AbstractParteTutorial
public class ParteTutorial7 extends AbstractParteTutorial {

        public ParteTutorial7() {
                // Llama al constructor de la clase base, pasando el número de parte 7.
                super(7);
        }

        /**
         * Implementación del método abstracto para definir el contenido de la Parte 7
         * (Cocinar Platillos).
         */
        @Override
        protected JsonArray crearContenidoJson() {
                JsonArray Array = new JsonArray();
                JsonObject jsonObject;
                String urlFrank = getUrlFrank(); // Usa el método de la clase base

                // PASO 1: Usar la Cocina
                jsonObject = new JsonObject();
                jsonObject.addProperty("url_image", getUrlBase());
                if (RoleplayWebsocketsRoomsInfo.getTutorialHabboRpManager().forceVisible) {
                        jsonObject.addProperty("isMinimized", 0);
                } else {
                        jsonObject.addProperty("isMinimized", 1);
                }
                jsonObject.addProperty("img", urlFrank + "frank_04.gif");
                jsonObject.addProperty("title", "Cocina tus primeros platillos");
                jsonObject.addProperty("desc",
                                "Acercate a tu cocina y presionala dos veces: <br>"
                                                + "<div class='flex'> "
                                                + "<section class='contentGif'>"
                                                + "<img class='gif' src='tutorial/png/objeto_cocina.png?v1' style='width: 100px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%); background: rgb(0 0 0 / 36%);'/> "
                                                + "</section> "
                                                + "</div>");
                // No hay evento asociado aquí, se asume que el tutorial avanza al interactuar
                // con el objeto.
                Array.add(jsonObject);

                return Array;
        }
}