package com.srth.plugin.Tutorial.partes;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;

// Extiende AbstractParteTutorial
public class ParteTutorial3 extends AbstractParteTutorial {

        public ParteTutorial3() {
                // Llama al constructor de la clase base, pasando el número de parte 3.
                super(3);
        }

        /**
         * Implementación del método abstracto para definir el contenido de la Parte 3.
         */
        @Override
        protected JsonArray crearContenidoJson() {
                JsonArray Array = new JsonArray();
                JsonObject jsonObject;
                String urlFrank = getUrlFrank(); // Usa el método de la clase base

                // PASO 1: Explicación de las Barras del Personaje (Hambre, Vida, Sueño)
                jsonObject = new JsonObject();
                jsonObject.addProperty("url_image", getUrlBase());
                if (RoleplayWebsocketsRoomsInfo.getTutorialHabboRpManager().forceVisible) {
                        jsonObject.addProperty("isMinimized", 0);
                } else {
                        jsonObject.addProperty("isMinimized", 1);
                }
                jsonObject.addProperty("img", urlFrank + "frank_04.gif");
                jsonObject.addProperty("title", "Sobre tu personaje");
                jsonObject.addProperty("desc",
                                "Como habrás notado, tu personaje tiene 3 barras: <br><br>"
                                                + "<div class='flex'> "
                                                + "<section class='contentGif'>"
                                                + "<b>Hambre</b><br>"
                                                + "<img class='gif' src='tutorial/png/hambre.png?v1' style='width: 150px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%);'/> "
                                                + "<span>Come platillos para recuperarla. <br><br> Consigue platillos de comida cocinándolos en tu casa o compralos en maquinas de alimentos.</span>"
                                                + "</section> "
                                                + "<section class='contentGif'>"
                                                + "<b>Vida</b><br>"
                                                + "<img class='gif' src='tutorial/png/vida.png?v1' style='width: 150px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%);'/> "
                                                + "<span>Manten tu hambre al 100 para llenarla. <br><br> Cada cierto tiempo disminuirá <br><br> Si recibes daño tu vida disminuirá.</span>"
                                                + "</section> "
                                                + "<section class='contentGif'>"
                                                + "<b>Sueño</b><br>"
                                                + "<img class='gif' src='tutorial/png/sueno.png?v1' style='width: 150px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%);'/> "
                                                + "<span>Duerme para conseguir energía. <br><br> Puedes usar la cama de culquier jugador para dormir.</span>"
                                                + "</section>"
                                                + "</div>"
                                                + "<br><span><b>¿Podrás mantener con vida tu personaje?</b></span>");
                Array.add(jsonObject);

                // PASO 2: Viaje al Supermercado (Evento para cambiar de sala)
                jsonObject = new JsonObject();
                jsonObject.addProperty("url_image", getUrlBase());
                if (RoleplayWebsocketsRoomsInfo.getTutorialHabboRpManager().forceVisible) {
                        jsonObject.addProperty("isMinimized", 0);
                } else {
                        jsonObject.addProperty("isMinimized", 1);
                }
                jsonObject.addProperty("img", urlFrank + "frank_04.gif");
                jsonObject.addProperty("title", "Visitemos el supermercado");
                jsonObject.addProperty("desc",
                                "Vamonos de compras al super, donde conseguirás tus primeros ingredientes para cocinar platillos de comida.<br>"
                                                + "<div class='flex'> "
                                                + "<section class='contentGif'>"
                                                + "<img class='gif' src='tutorial/png/supermercado.png?v1' style='width: 150px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%);'/> "
                                                + "<b>En el supermercado podrás: </b><br><br>"
                                                + "<span>- Comprar ingredientes para cocinar. <br><br> - Comprar platillos de comida ya preparados.</span>"
                                                + "</section> "
                                                + "</div>");
                jsonObject.addProperty("event", "GoToRoomRecorridoSupermercado"); // Evento para cambiar de sala
                Array.add(jsonObject);

                return Array;
        }
}