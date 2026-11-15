package com.srth.plugin.Tutorial.partes;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;

// Extiende AbstractParteTutorial
public class ParteTutorial2 extends AbstractParteTutorial {

        public ParteTutorial2() {
                // Llama al constructor de la clase base, pasando el número de parte 2.
                // Esto inicializa la parte y registra el contenido que definimos abajo.
                super(2);
        }

        /**
         * Implementación del método abstracto para definir el contenido de la Parte 2.
         */
        @Override
        protected JsonArray crearContenidoJson() {
                JsonArray Array = new JsonArray();
                JsonObject jsonObject;
                String urlFrank = getUrlFrank(); // Usa el método de la clase base

                // PASO 1: Explicación del Centro Correccional
                jsonObject = new JsonObject();
                jsonObject.addProperty("url_image", getUrlBase());
                if (RoleplayWebsocketsRoomsInfo.getTutorialHabboRpManager().forceVisible) {
                        jsonObject.addProperty("isMinimized", 0);
                } else {
                        jsonObject.addProperty("isMinimized", 1);
                }
                jsonObject.addProperty("img", urlFrank + "frank_04.gif");
                jsonObject.addProperty("title", "Centro correcional");
                jsonObject.addProperty("desc",
                                "Llegaste al centro penintenciario, lugar donde se espían todos los delitos cometidos en el hotel. <br><br>"
                                                + "Verás, muchas veces al anochecer, los jugadores entran en guerrillas de bandas o simplemente cometen asesinatos y robos contra los ciudadanos que esten fuera del hospital, es decir, en zonas inseguras... <br><br>"
                                                + "Es por ello que existe el cuerpo de seguridad policial... Que se encarga de penalizar a estos malechores.");
                Array.add(jsonObject);

                // PASO 2: Estados del Día y Seguridad (Final de la Parte 2)
                jsonObject = new JsonObject();
                jsonObject.addProperty("url_image", getUrlBase());
                if (RoleplayWebsocketsRoomsInfo.getTutorialHabboRpManager().forceVisible) {
                        jsonObject.addProperty("isMinimized", 0);
                } else {
                        jsonObject.addProperty("isMinimized", 1);
                }
                jsonObject.addProperty("img", urlFrank + "frank_04.gif");
                jsonObject.addProperty("title", "Mantente alerta");
                jsonObject.addProperty("desc",
                                "Te recomendamos siempre mantenerte alerta a los estados del día: <br><br>"
                                                + "<div class='flex'> "
                                                + "<section class='contentGif'>"
                                                + "<b>Día</b><br>"
                                                + "<img class='gif' src='tutorial/png/dia.png?v1' style='width: 150px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%);'/> "
                                                + "<span>Calles seguras, pocos robos en la ciudad.</span>"
                                                + "</section> "
                                                + "<section class='contentGif'>"
                                                + "<b>Tarde</b><br>"
                                                + "<img class='gif' src='tutorial/png/tarde.png?v1' style='width: 150px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%);'/> "
                                                + "<span>Calles medio seguras, los delincuentes están al asecho.</span>"
                                                + "</section> "
                                                + "<section class='contentGif'>"
                                                + "<b>Noche</b><br>"
                                                + "<img class='gif' src='tutorial/png/noche.png?v1' style='width: 150px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%);'/> "
                                                + "<span>Calles inseguras, muchos robos y delitos por doquier.</span>"
                                                + "</section>"
                                                + "</div>");
                jsonObject.addProperty("event", "incrementParteTutorial"); // Evento para pasar a la siguiente parte
                Array.add(jsonObject);

                return Array;
        }
}