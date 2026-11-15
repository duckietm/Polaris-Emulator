package com.srth.plugin.Tutorial.partes;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;

// Extiende AbstractParteTutorial
public class ParteTutorial4 extends AbstractParteTutorial {

        public ParteTutorial4() {
                // Llama al constructor de la clase base, pasando el número de parte 4.
                super(4);
        }

        /**
         * Implementación del método abstracto para definir el contenido de la Parte 4
         * (Comprando en el Supermercado).
         */
        @Override
        protected JsonArray crearContenidoJson() {
                JsonArray Array = new JsonArray();
                JsonObject jsonObject;
                String urlFrank = getUrlFrank(); // Usa el método de la clase base

                // PASO 1: Objetos y Tipos de Compra
                jsonObject = new JsonObject();
                jsonObject.addProperty("url_image", getUrlBase());
                if (RoleplayWebsocketsRoomsInfo.getTutorialHabboRpManager().forceVisible) {
                        jsonObject.addProperty("isMinimized", 0);
                } else {
                        jsonObject.addProperty("isMinimized", 1);
                }
                jsonObject.addProperty("img", urlFrank + "frank_04.gif");
                jsonObject.addProperty("title", "Comprando en el supermercado");
                jsonObject.addProperty("desc",
                                "En el supermercado hay varios objetos que podrás utilizar: <br><br>"
                                                + "<div class='flex'> "
                                                + "<section class='contentGif'>"
                                                + "<b>Comprar ingredientes</b><br>"
                                                + "<img class='gif' src='tutorial/png/objeto_ingredientes.png?v1' style='width: 60px; height: 100px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%); background:rgb(0 0 0 / 36%);'/><br>"
                                                + "<span>Presionalo dos veces y te muestra el listado de ingredientes y sus precios.</span>"
                                                + "</section> "
                                                + "<section class='contentGif'>"
                                                + "<b>Comprar comida</b><br>"
                                                + "<div class='flex'> <img class='gif' src='tutorial/png/objeto_platillos.png?v1' style='width: 60px; height: 100px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%); background:rgb(0 0 0 / 36%);'/> <img class='gif' src='tutorial/png/objeto_pizzas.png?v1' style='width: 60px; height: 100px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%); background:rgb(0 0 0 / 36%);'/> <img class='gif' src='tutorial/png/objeto_papas.png?v1' style='width: 60px; height: 100px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%); background:rgb(0 0 0 / 36%);'/> </div><br>"
                                                + "<span>Presionalo dos veces y te muestra el listado de platillos y sus precios.</span>"
                                                + "</section> "
                                                + "<br>"
                                                + "</div>");
                Array.add(jsonObject);

                // PASO 2: Demostración y Acción (Comprar Ingredientes)
                jsonObject = new JsonObject();
                jsonObject.addProperty("url_image", getUrlBase());
                if (RoleplayWebsocketsRoomsInfo.getTutorialHabboRpManager().forceVisible) {
                        jsonObject.addProperty("isMinimized", 0);
                } else {
                        jsonObject.addProperty("isMinimized", 1);
                }
                jsonObject.addProperty("img", urlFrank + "frank_04.gif");
                jsonObject.addProperty("title", "¿Cómo se hace?");
                jsonObject.addProperty("desc",
                                "Sigue los pasos que te mostramos a continuación:<br>"
                                                + "<div class='flex'> "
                                                + "<section class='contentGif'>"
                                                + "<img class='gif' src='tutorial/gif/buyingIngredientes.gif?v1' style='width: 150px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%);'/> "
                                                +
                                                "<b>Compra ingredientes: </b><br><br>"
                                                + "<span>- Sirven para cocinar platillos de comida en tu casa.</span>"
                                                + "</section> "
                                                + "<section class='contentGif'>"
                                                + "<img class='gif' src='tutorial/gif/buyingPlatillos.gif?v1' style='width: 150px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%);'/> "
                                                +
                                                "<b>Compra platillos: </b><br><br>"
                                                + "<span>- Es comida rápida, por ende suele ser costosa.</span>"
                                                + "</section> "
                                                + "</div><br>"
                                                + "<span>Ahora que sabes esto: <br></span><b>Acercate al mostrador de vegetales y compra 10 ingredientes variados para continuar...</b>"
                                                + "<br>");

                jsonObject.addProperty("event", "buyingIngredientesGoTo");
                Array.add(jsonObject);

                return Array;
        }
}