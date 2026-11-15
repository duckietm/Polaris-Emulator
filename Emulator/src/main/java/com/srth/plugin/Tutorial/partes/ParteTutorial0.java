package com.srth.plugin.Tutorial.partes;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;

// Ahora extiende la clase abstracta para heredar la lógica
public class ParteTutorial0 extends AbstractParteTutorial {

        public ParteTutorial0() {
                super(0); // Llama al constructor de la clase base, pasando el número de parte 0
        }

        /**
         * Implementación del método abstracto para definir el contenido de la Parte 0.
         * Toda la lógica de creación de JsonArray y JsonObject va aquí.
         */
        @Override
        protected JsonArray crearContenidoJson() {
                JsonArray Array = new JsonArray();
                JsonObject jsonObject;
                // Obtiene la URL a través del método de la clase base, sin repetir el código
                String urlFrank = getUrlFrank();

                // PASO 1: Bienvenida
                jsonObject = new JsonObject();
                jsonObject.addProperty("url_image", getUrlBase());
                if (RoleplayWebsocketsRoomsInfo.getTutorialHabboRpManager().forceVisible) {
                        jsonObject.addProperty("isMinimized", 0);
                } else {
                        jsonObject.addProperty("isMinimized", 1);
                }
                jsonObject.addProperty("img", urlFrank + "frank_04.gif");
                jsonObject.addProperty("title", "¡Bienvenido al modo Roleplay!");
                jsonObject.addProperty("desc",
                                "Un gusto tenerte por aqui, te vamos a explicar el funcionamiento del modo roleplay.<br><br> ¿Estas preparado?");
                Array.add(jsonObject);

                // PASO 2: Ubicación (Hospital)
                jsonObject = new JsonObject();
                jsonObject.addProperty("url_image", getUrlBase());
                if (RoleplayWebsocketsRoomsInfo.getTutorialHabboRpManager().forceVisible) {
                        jsonObject.addProperty("isMinimized", 0);
                } else {
                        jsonObject.addProperty("isMinimized", 1);
                }
                jsonObject.addProperty("img", urlFrank + "frank_05.gif");
                jsonObject.addProperty("gif", urlFrank + "tutorial/gif/giphy.gif");
                jsonObject.addProperty("title", "¡Te encuentras en el Hospital!");
                jsonObject.addProperty("desc",
                                "Como te das cuenta, <b>estás en el hospital oficial del modo roleplay.</b> <br><br> Acá es donde siempre serás enviado cuando mueras y donde serás atendido por el personal médico de turno. <br><br> En caso de no haber personal de turno para atenderte, podras comer tu propia comida para recuperar tu vida.<br><br> Continúa para mostrarte como podrás hacerlo...");
                Array.add(jsonObject);

                // PASO 3: Recuperar Vida (Final de la Parte 0)
                jsonObject = new JsonObject();
                jsonObject.addProperty("url_image", getUrlBase());
                if (RoleplayWebsocketsRoomsInfo.getTutorialHabboRpManager().forceVisible) {
                        jsonObject.addProperty("isMinimized", 0);
                } else {
                        jsonObject.addProperty("isMinimized", 1);
                }
                jsonObject.addProperty("img", urlFrank + "frank_04.gif");
                jsonObject.addProperty("gif", urlFrank + "tutorial/gif/giphy.gif");
                jsonObject.addProperty("title", "Funcionamiento del modo roleplay");
                jsonObject.addProperty("desc",
                                "El modo roleplay, se basa, en mantener con vida a tu personaje y realizar acciones simulando la vida real. <br><br>"
                                                + "¡Seguro te pasara que tu personaje morirá por no atenderlo! <br><br> <b>Acá te indicamos como podrás recupar tu vida en esos momentos:</b> <br><br>"
                                                + "<div class='flex'> <section class='contentGif' style='width: 340px;'><b>Curado por un personal</b><br></b><img class='gif' style='height: 240px;' src='tutorial/gif/curarUsuarioMedico.gif'/> <span>Serás enviado al hospital y puedes esperar a ser curado por un personal médico.</span> </section> <section class='contentGif' style='width: 340px;'><b>Comiendo platillos</b><br></b><img class='gif' style='height: 240px;' src='tutorial/gif/usePlatillosRP.gif'/> <span>En caso de que no haya personal medico activo, podrás comer tus propios platillos para recuperar vida.</span></section>"
                                                + "</div><br><br>");
                jsonObject.addProperty("event", "incrementParteTutorialInicio");
                Array.add(jsonObject);

                return Array;
        }
}