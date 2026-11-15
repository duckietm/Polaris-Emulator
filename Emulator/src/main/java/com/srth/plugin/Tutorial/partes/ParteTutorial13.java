package com.srth.plugin.Tutorial.partes;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;

// Extiende AbstractParteTutorial
public class ParteTutorial13 extends AbstractParteTutorial {

        public ParteTutorial13() {
                // Llama al constructor de la clase base, pasando el número de parte 10.
                super(13);
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
                jsonObject.addProperty("title", "¡Utiliza tu horno para cocinar!");
                jsonObject.addProperty("desc",
                                "<b>Podrás utilizar tu horno para cocinar de una forma más rápida</b><br><br>"
                                                + "<span>Te vamos a regalar los ingredientes necesarios para que cocines tu primera tanda en el horno.</span>"
                                                + "<div class='flex'> "
                                                + "<section class='contentGif'>"
                                                + "<div class='flex'><img class='gif' src='tutorial/png/objeto_horno.png?v1' style='width: 60px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%); background: rgb(0 0 0 / 36%);'/> <img class='gif' src='tutorial/png/ingredientes.png?v1' style='width: 200px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%); background: rgb(0 0 0 / 36%);'/> </div> "
                                                + "</section> "
                                                + "</div>");
                jsonObject.addProperty("event", "proccesGiveIngredientesHorno");
                Array.add(jsonObject);

                return Array;
        }
}