package com.srth.plugin.Tutorial.partes;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;

// Importante: Extiende la clase base abstracta
public class ParteTutorial1 extends AbstractParteTutorial {

        public ParteTutorial1() {
                // Llama al constructor de la clase base, pasando el número de parte 1.
                // Esto automáticamente maneja la obtención de URLs y el registro.
                super(1);
        }

        /**
         * Implementación del método abstracto para definir el contenido de la Parte 1.
         * Solo se enfoca en crear el JsonArray con los pasos específicos.
         */
        @Override
        protected JsonArray crearContenidoJson() {
                JsonArray Array = new JsonArray();
                JsonObject jsonObject;
                // Obtiene la URL a través del método de la clase base.
                String urlFrank = getUrlFrank();

                // PASO 1: Inicio del Recorrido
                jsonObject = new JsonObject();
                jsonObject.addProperty("url_image", getUrlBase());
                if (RoleplayWebsocketsRoomsInfo.getTutorialHabboRpManager().forceVisible) {
                        jsonObject.addProperty("isMinimized", 0);
                } else {
                        jsonObject.addProperty("isMinimized", 1);
                }
                jsonObject.addProperty("img", urlFrank + "frank_04.gif");
                jsonObject.addProperty("title", "Recorrido personalizado");
                jsonObject.addProperty("desc",
                                "¡Voy a darte un recorrido por la ciudad! para que conozcas las salas oficiales del modo roleplay donde podrás interacturar con todos los demás jugadores... ¿Bien?");
                Array.add(jsonObject);

                // PASO 2: Viaje al Centro Penitenciario
                jsonObject = new JsonObject();
                jsonObject.addProperty("url_image", getUrlBase());
                if (RoleplayWebsocketsRoomsInfo.getTutorialHabboRpManager().forceVisible) {
                        jsonObject.addProperty("isMinimized", 0);
                } else {
                        jsonObject.addProperty("isMinimized", 1);
                }
                jsonObject.addProperty("img", urlFrank + "frank_04.gif");
                jsonObject.addProperty("title", "Viaje al centro penintenciario");
                jsonObject.addProperty("desc",
                                "¡Muy bien! Te regalaremos un viaje gratuito en un taxi para que visites: <br><br><div class='flex'> <section class='contentGif'><b>El Centro penitenciario</b><br><img class='gif' src='tutorial/png/centro_penitenciario.png' style='width: 150px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%);'/> </section> </div> <br><span>Vamos a ello...</span>");
                // Evento específico de esta parte
                jsonObject.addProperty("event", "GoToRoomRecorridoCentroPenintenciario");
                Array.add(jsonObject);

                // Retorna el array con el contenido, sin necesidad de guardarlo como variable
                // de clase.
                return Array;
        }
}