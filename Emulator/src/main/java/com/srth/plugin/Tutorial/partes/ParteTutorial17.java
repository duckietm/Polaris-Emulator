package com.srth.plugin.Tutorial.partes;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;

// Extiende AbstractParteTutorial
public class ParteTutorial17 extends AbstractParteTutorial {

        public ParteTutorial17() {
                // Llama al constructor de la clase base, pasando el número de parte 10.
                super(17);
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
                jsonObject.addProperty("title", "Muy bien... ¡Ya casi lo consigues!");
                jsonObject.addProperty("desc",
                                "<span>Lo estas haciendo genial, ya que llegaste a este punto, pudiste notar, que...</span><br><br>"
                                                + "<div>"
                                                + "<img src='tutorial/png/dinero.png?v1'>"
                                                + "</div>"
                                                + "<span>Es muy importante obtener dinero para poder sobrevir en el modo roleplay.</span>"
                                                + "<br>"
                                                + "<span>Puedes ver el dinero que llevas contigo en tu inventario roleplay...</span>"
                                                + "<br>"
                                                + "<span>¡Continúa para mostrarte!</span>");
                Array.add(jsonObject);

                jsonObject = new JsonObject();
                jsonObject.addProperty("url_image", getUrlBase());
                if (RoleplayWebsocketsRoomsInfo.getTutorialHabboRpManager().forceVisible) {
                        jsonObject.addProperty("isMinimized", 0);
                } else {
                        jsonObject.addProperty("isMinimized", 1);
                }
                jsonObject.addProperty("img", urlFrank + "frank_04.gif");
                jsonObject.addProperty("title", "Veamos tu dinero actual");
                jsonObject.addProperty("desc",
                                "<span>Abre tu inventario roleplay y mira tu dinero</span><br>"
                                                + "<section class='contentGif'>"
                                                + "<img class='gif' src='tutorial/gif/viendoElDineroInventario.gif?v1' style='width: 350px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%); background: rgb(0 0 0 / 36%);'/> "
                                                + "</section> "
                                                + "<span>Recuerda que acabas de depositar tu dinero en el cajero automático, si lo necesitas, también podrás retirarlo... <b>¡Voy a mostrarte como!</b></span>");
                Array.add(jsonObject);

                jsonObject = new JsonObject();
                jsonObject.addProperty("url_image", getUrlBase());
                if (RoleplayWebsocketsRoomsInfo.getTutorialHabboRpManager().forceVisible) {
                        jsonObject.addProperty("isMinimized", 0);
                } else {
                        jsonObject.addProperty("isMinimized", 1);
                }
                jsonObject.addProperty("img", urlFrank + "frank_04.gif");
                jsonObject.addProperty("title", "¿Cómo retirar mi dinero?");
                jsonObject.addProperty("desc",
                                "<span>De esta forma puedes retirar tu dinero (Debes hacerlo):</span><br>"
                                                + "<section class='contentGif'>"
                                                + "<img class='gif' src='tutorial/gif/retirarDineroCajero.gif?v1' style='width: 350px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%); background: rgb(0 0 0 / 36%);'/> "
                                                + "</section> "
                                                + "<br> "
                                                + "<span>Es importante que retires 10 cash de tu cajero, para que los lleves en fisico contigo... (Te servirá para comprar una caña para pescar en el lago)... <br><br> Ahora que sabes esto...<br> <b>¡Vayamos al lago a que obtengas tus primeros ingresos!</b></span>");
                Array.add(jsonObject);

                jsonObject = new JsonObject();
                jsonObject.addProperty("url_image", getUrlBase());
                if (RoleplayWebsocketsRoomsInfo.getTutorialHabboRpManager().forceVisible) {
                        jsonObject.addProperty("isMinimized", 0);
                } else {
                        jsonObject.addProperty("isMinimized", 1);
                }
                jsonObject.addProperty("img", urlFrank + "frank_04.gif");
                jsonObject.addProperty("title", "Dirigete al lago de la ciudad");
                jsonObject.addProperty("desc",
                                "<span>¿Ya tienes 10 cash en tu inventario fisico? Entonces...</span><br><br>" +
                                                "<span>Utiliza las flechas de esta dirección para ir al lago</span><br>"
                                                + "<section class='contentGif'>"
                                                + "<img class='gif' src='tutorial/png/flechasciudad.png?v1' style='width: 350px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%); background: rgb(0 0 0 / 36%);'/> "
                                                + "</section>"
                                                + "<span> Camina y ponte encima de ellas para continuar...</span>");
                Array.add(jsonObject);

                return Array;
        }
}