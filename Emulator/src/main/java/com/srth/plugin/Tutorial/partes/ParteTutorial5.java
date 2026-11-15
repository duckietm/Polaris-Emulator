package com.srth.plugin.Tutorial.partes;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;

// Extiende AbstractParteTutorial
public class ParteTutorial5 extends AbstractParteTutorial {

        public ParteTutorial5() {
                // Llama al constructor de la clase base, pasando el número de parte 5.
                super(5);
        }

        /**
         * Implementación del método abstracto para definir el contenido de la Parte 5
         * (Crear Casa).
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
                jsonObject.addProperty("img", urlFrank + "frank_04.gif");
                jsonObject.addProperty("title", "CREA TU CASA");
                jsonObject.addProperty("desc",
                                "Es momento de crear tu casa, el lugar donde estarás seguro y podrás cocinar tus propios platillos de comida para mantener con vida a tu personaje. <br><br> ¡Vamos a ello!");
                Array.add(jsonObject);

                // PASO 2: Proceso de Creación/Visita de la Casa
                jsonObject = new JsonObject();
                jsonObject.addProperty("url_image", getUrlBase());
                if (RoleplayWebsocketsRoomsInfo.getTutorialHabboRpManager().forceVisible) {
                        jsonObject.addProperty("isMinimized", 0);
                } else {
                        jsonObject.addProperty("isMinimized", 1);
                }
                jsonObject.addProperty("img", urlFrank + "frank_04.gif");
                jsonObject.addProperty("title", "Proceso de visitar tu casa");
                jsonObject.addProperty("desc",
                                "Cada vez que quieras visitar o regresar a tu casa te montarás en un taxi y recorrerás toda la ciudad<br> <b>hasta llegar a la Calle del Puente.</b><br><br>La Calle del Puente será la central donde se reunirán todos los usuarios que entran o salen de sus casas...<br><br>"
                                                + "<div class='flex'> "
                                                + "<section class='contentGif'>"
                                                + "<b>Ahora que sabes esto...</b><br>"
                                                + "<img class='gif' src='tutorial/png/processCrearCasa.png?v1' style='width: 150px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%);'/> "
                                                + "</section> "
                                                + "</div>"
                                                + "<span><b>Continúa para visitar tu casa ahora...</b></span>");
                // Evento que inicia el proceso de creación/visita de la casa
                jsonObject.addProperty("event", "processCrearHomeRoleplay");
                Array.add(jsonObject);

                return Array;
        }
}