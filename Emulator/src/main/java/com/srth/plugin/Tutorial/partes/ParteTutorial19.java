package com.srth.plugin.Tutorial.partes;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;

// Extiende AbstractParteTutorial
public class ParteTutorial19 extends AbstractParteTutorial {

        public ParteTutorial19() {
                // Llama al constructor de la clase base, pasando el número de parte 10.
                super(19);
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
                jsonObject.addProperty("title", "Tutorial básico completado");
                jsonObject.addProperty("desc",
                                "<b>¡Lo hiciste genial!</b><br>Lograste cumpletar el tutorial básico... <br><br> A partir de este momento tu vida, hambre y sueño comenzará a reducir con el tiempo, asegúrate de mantener con vida a tu personaje..."
                                                + "<br><br>"
                                                + "<span>¡Recuerda llevar contigo platillos de comida a todas las salas donde vayas!</span>"
                                                + "<br><br><br>"
                                                + "<span><b>Tengo otros tutoriales que te interesarán:</b><br><br> Se trata de más formas de obtener dinero y más cosas variadas...<br> ¡Puedes verlos presionando el botón que aparecerá a continuación, arriba de las tareas roleplay! <br>(Se encontrará arriba de tu vida, hambre, y sueño)</span>");
                jsonObject.addProperty("event", "finishTutorial");
                Array.add(jsonObject);

                return Array;
        }
}