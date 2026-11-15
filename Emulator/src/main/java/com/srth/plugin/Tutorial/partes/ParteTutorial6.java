package com.srth.plugin.Tutorial.partes;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;

// Extiende AbstractParteTutorial
public class ParteTutorial6 extends AbstractParteTutorial {

        public ParteTutorial6() {
                // Llama al constructor de la clase base, pasando el número de parte 6.
                super(6);
        }

        /**
         * Implementación del método abstracto para definir el contenido de la Parte 6
         * (Estar en Casa).
         */
        @Override
        protected JsonArray crearContenidoJson() {
                JsonArray Array = new JsonArray();
                JsonObject jsonObject;
                String urlFrank = getUrlFrank(); // Usa el método de la clase base

                // PASO 1: Introducción a la Casa
                jsonObject = new JsonObject();
                jsonObject.addProperty("url_image", getUrlBase());
                if (RoleplayWebsocketsRoomsInfo.getTutorialHabboRpManager().forceVisible) {
                        jsonObject.addProperty("isMinimized", 0);
                } else {
                        jsonObject.addProperty("isMinimized", 1);
                }
                jsonObject.addProperty("img", urlFrank + "frank_room.gif");
                jsonObject.addProperty("title", "¡Te encuentras en casa!");
                jsonObject.addProperty("desc",
                                "Ya tienes un lugar al que regresar siempre que quieras estar seguro. <br><br> Podrás decorarlo de la forma que gustes y invitar a vivir contigo a quien tú desees.");
                Array.add(jsonObject);

                // PASO 2: Abrir la Puerta
                jsonObject = new JsonObject();
                jsonObject.addProperty("url_image", getUrlBase());
                if (RoleplayWebsocketsRoomsInfo.getTutorialHabboRpManager().forceVisible) {
                        jsonObject.addProperty("isMinimized", 0);
                } else {
                        jsonObject.addProperty("isMinimized", 1);
                }
                jsonObject.addProperty("img", urlFrank + "frank_04.gif");
                jsonObject.addProperty("title", "Presiona tu puerta y entra");
                jsonObject.addProperty("desc",
                                "Da doble click o un solo click sobre tu puerta para ver el menu y abrirla: <br><br>"
                                                + "<div class='flex'> "
                                                + "<section class='contentGif'>"
                                                + "<b>Como hacerlo:</b><br>"
                                                + "<img class='gif' src='tutorial/gif/openPuertaHome.gif?v1' style='width: 150px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%);'/> "
                                                + "</section> "
                                                + "</div>");
                Array.add(jsonObject);

                return Array;
        }
}