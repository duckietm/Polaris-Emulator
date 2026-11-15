package com.srth.plugin.Tutorial.partes;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;

// Extiende AbstractParteTutorial
public class ParteTutorial10 extends AbstractParteTutorial {

        public ParteTutorial10() {
                // Llama al constructor de la clase base, pasando el número de parte 10.
                super(10);
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
                jsonObject.addProperty("title", "¿Cómo puedo comer mis platillos?");
                jsonObject.addProperty("desc",
                                "Estas son las formas en la que podrás comer tus platillos de comida: <br><br>"
                                                + "<b> Usa tu inventario principal:</b> <br>"
                                                + "<div class='flex'> "
                                                + "<section class='contentGif'>"
                                                + "<img class='gif' src='tutorial/gif/usePlatillosInventory.gif?v1' style='width: 300px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%); background: rgb(0 0 0 / 36%);'/> "
                                                + "</section> "
                                                + "</div></b>"
                                                + "<b>¡Hazlo ahora para continuar!</b>");
                // No se incluye el evento aquí, ya que el tutorial avanza al interactuar con el
                // objeto.
                Array.add(jsonObject);

                return Array;
        }
}