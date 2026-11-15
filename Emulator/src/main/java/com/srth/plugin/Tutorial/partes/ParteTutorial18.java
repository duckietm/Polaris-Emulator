package com.srth.plugin.Tutorial.partes;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;

// Extiende AbstractParteTutorial
public class ParteTutorial18 extends AbstractParteTutorial {

        public ParteTutorial18() {
                // Llama al constructor de la clase base, pasando el número de parte 10.
                super(18);
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
                jsonObject.addProperty("title", "Compra una caña");
                jsonObject.addProperty("desc",
                                "<b>Acercate al pescador y di comprar</b><br>Te mostrará las cañas que tiene disponibles para venderte, debes comprarle la caña basica..."
                                                + ""
                                                + "<div class='flex'> "
                                                + "<section class='contentGif'>"
                                                + "<img class='gif' src='tutorial/gif/buyCanadePescar.gif?v1' style='width: 350px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%); background: rgb(0 0 0 / 36%);'/>"
                                                + "</section> "
                                                + "</div>"
                                                + "<br><span>¿Lograste comprarle la caña? Te enseñaremos como equiparla...</span>");
                Array.add(jsonObject);

                jsonObject = new JsonObject();
                jsonObject.addProperty("url_image", getUrlBase());
                if (RoleplayWebsocketsRoomsInfo.getTutorialHabboRpManager().forceVisible) {
                        jsonObject.addProperty("isMinimized", 0);
                } else {
                        jsonObject.addProperty("isMinimized", 1);
                }
                jsonObject.addProperty("img", urlFrank + "frank_04.gif");
                jsonObject.addProperty("title", "¿Cómo equipo mi caña y cómo puedo pescar?");
                jsonObject.addProperty("desc",
                                "<span>Sigue estos pasos para equipar tu caña y pescar tus primeros peces.</span>"
                                                + ""
                                                + "<div class='flex'> "
                                                + "<section class='contentGif'>"
                                                + "<img class='gif' src='tutorial/gif/pescandoPecesCana.gif?v1' style='width: 350px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%); background: rgb(0 0 0 / 36%);'/>"
                                                + "</section> "
                                                + "</div>"
                                                + "<b>Hazlo tú para continuar...</b>");
                Array.add(jsonObject);

                jsonObject = new JsonObject();
                jsonObject.addProperty("url_image", getUrlBase());
                if (RoleplayWebsocketsRoomsInfo.getTutorialHabboRpManager().forceVisible) {
                        jsonObject.addProperty("isMinimized", 0);
                } else {
                        jsonObject.addProperty("isMinimized", 1);
                }
                jsonObject.addProperty("img", urlFrank + "frank_04.gif");
                jsonObject.addProperty("title", "¿Cómo obtengo el dinero por mis peces?");
                jsonObject.addProperty("desc",
                                "<span>Debes acercarte al pescador y decirle vender y luego confirmarle con si, para venderle todos tus peces.</span>"
                                                + ""
                                                + "<div class='flex'> "
                                                + "<section class='contentGif'>"
                                                + "<img class='gif' src='tutorial/gif/venderPecesPescador.gif?v1' style='width: 350px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%); background: rgb(0 0 0 / 36%);'/>"
                                                + "</section> "
                                                + "</div>"
                                                + "<b>Vendele los peces para finalizar...</b>");
                Array.add(jsonObject);

                return Array;
        }
}