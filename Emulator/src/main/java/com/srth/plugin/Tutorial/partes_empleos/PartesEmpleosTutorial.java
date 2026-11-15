package com.srth.plugin.Tutorial.partes_empleos;

import java.sql.SQLException;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Tutorial.HabboTutorial;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.websocket.Habbo.HabboClient;

import gnu.trove.map.hash.THashMap;

public class PartesEmpleosTutorial {

        public PartesEmpleosTutorial() {

        }

        public JsonArray getPartes(String type, Boolean forceVisible) {

                String url_hotel_base = RoleplayWebsocketsRoomsInfo.getConfig()
                                .getValue("global.url.hotel.base");
                String urlFrank = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url_images")
                                .replace("%url.hotel.base%", url_hotel_base) + "frank/";
                String urlBase = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url_images")
                                .replace("%url.hotel.base%", url_hotel_base);

                JsonArray jsonArray = new JsonArray();
                JsonObject jsonObject;

                String versionStr = type;
                switch (versionStr) {
                        case "1.1.0":
                                jsonObject = new JsonObject();
                                jsonObject.addProperty("url_image", urlBase);
                                jsonObject.addProperty("informal", true);
                                jsonObject.addProperty("img",
                                                urlFrank + "frank_04.gif");
                                jsonObject.addProperty("title", "¡Activa tu empleo de médico!");
                                jsonObject.addProperty("desc",
                                                "Para iniciar, debes activar el empleo de médico. <br><br>"
                                                                + "<b>Como hacerlo:</b><br>"
                                                                + "<div class='flex'> "
                                                                + "<section class='contentGif flex'>"
                                                                + "<img class='gif' src='tutorial/png/action_point.png?v1' style='width: 50px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%); background: rgb(0 0 0 / 36%);'/> "
                                                                + "<img class='gif' src='tutorial/png/scanner.png?v1' style='width: 70px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%); background: rgb(0 0 0 / 36%);'/> "
                                                                + "</section> "
                                                                + "</div>"

                                                                + "Ponte encima de este escaner, dentro del hospital para continuar...");
                                jsonArray.add(jsonObject);
                                break;
                        case "1.1.1":
                                jsonObject = new JsonObject();
                                jsonObject.addProperty("url_image", urlBase);

                                jsonObject.addProperty("informal", true);
                                if (forceVisible) {
                                        jsonObject.addProperty("isMinimized", 0);
                                } else {
                                        jsonObject.addProperty("isMinimized", 1);
                                }
                                jsonObject.addProperty("img",
                                                urlFrank + "frank_04.gif");
                                jsonObject.addProperty("title", "¡Obtén una jeringa!");
                                jsonObject.addProperty("desc",
                                                "¡Acercate al gabinete y saca una jeringa! <br><br>"
                                                                + "<b>Como hacerlo:</b><br>"
                                                                + "<div class='flex'> "
                                                                + "<section class='contentGif flex'>"
                                                                + "<img class='gif' src='tutorial/png/hospital_gabinete_jeringa.png?v1' style='width: 100px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%); background: rgb(0 0 0 / 36%);'/> "
                                                                + "<img class='gif' src='tutorial/png/action_point.png?v1' style='width: 100px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%); background: rgb(0 0 0 / 36%);'/> "
                                                                + "</section> "
                                                                + "</div>"
                                                                +
                                                                "Acercate al action point, y presiona una o dos veces el gabinete.");
                                jsonArray.add(jsonObject);
                                break;

                        case "1.1.2":
                                jsonObject = new JsonObject();
                                jsonObject.addProperty("url_image", urlBase);
                                jsonObject.addProperty("informal", true);
                                if (forceVisible) {
                                        jsonObject.addProperty("isMinimized", 0);
                                } else {
                                        jsonObject.addProperty("isMinimized", 1);
                                }
                                jsonObject.addProperty("reiniciarpage", 1);
                                jsonObject.addProperty("img",
                                                urlFrank + "frank_04.gif");
                                jsonObject.addProperty("title", "¡Equipa tu jeringa y cura a un usuario!");
                                jsonObject.addProperty("desc",
                                                "¡Demosle uso a la jeringa de medico y traigamos de nuevo a la vida a un jugador! <br><br>"
                                                                + "<b>Como hacerlo:</b><br>"
                                                                + "<div class='flex'> "
                                                                + "<section class='contentGif flex'>"
                                                                + "<img class='gif' src='tutorial/gif/curarUsuarioMedico.gif?v1' style='width: 350px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%); background: rgb(0 0 0 / 36%);'/> "
                                                                + "</section> "
                                                                + "</div>"
                                                                +
                                                                "<b>¡Ya lo tienes!</b> ahora que sabes que función deberás cumplir puedes intentar ejercer el empleo cuando te plazca");
                                jsonObject.addProperty("event", "finaliceTutorialMedico"); // Evento

                                jsonArray.add(jsonObject);
                                break;

                        case "1.2.0":
                                jsonObject = new JsonObject();
                                jsonObject.addProperty("url_image", urlBase);
                                jsonObject.addProperty("informal", true);
                                jsonObject.addProperty("reiniciarpage", 1);
                                jsonObject.addProperty("img",
                                                urlFrank + "frank_04.gif");
                                jsonObject.addProperty("title", "¡Dirigete al basurero!");
                                jsonObject.addProperty("desc",
                                                "¡Usa el taxi para llegar al basurero o dirigete a el por la ciudad! <br><br>"
                                                                + "<div class='flex'> "
                                                                + "<section class='contentGif flex'>"
                                                                + "<img class='gif' src='tutorial/png/basurero.png?v1' style='width: 200px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%); background: rgb(0 0 0 / 36%);'/> "
                                                                + "</section> "
                                                                + "</div>");
                                jsonArray.add(jsonObject);

                                jsonObject = new JsonObject();
                                jsonObject.addProperty("url_image", urlBase);
                                jsonObject.addProperty("informal", true);
                                jsonObject.addProperty("img",
                                                urlFrank + "frank_04.gif");
                                jsonObject.addProperty("title", "¡Activa tu empleo de basurero!");
                                jsonObject.addProperty("desc",
                                                "Para iniciar, debes activar el empleo de basurero. <br><br>"
                                                                + "<b>Como hacerlo:</b><br>"
                                                                + "<div class='flex'> "
                                                                + "<section class='contentGif flex'>"
                                                                + "<img class='gif' src='tutorial/png/action_point.png?v1' style='width: 50px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%); background: rgb(0 0 0 / 36%);'/> "
                                                                + "<img class='gif' src='tutorial/png/scanner.png?v1' style='width: 70px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%); background: rgb(0 0 0 / 36%);'/> "
                                                                + "</section> "
                                                                + "</div>"

                                                                + "Ponte encima de este escaner, dentro del basurero para continuar...");
                                jsonArray.add(jsonObject);
                                break;
                        case "1.2.1":
                                jsonObject = new JsonObject();
                                jsonObject.addProperty("url_image", urlBase);
                                jsonObject.addProperty("informal", true);
                                if (forceVisible) {
                                        jsonObject.addProperty("isMinimized", 0);
                                } else {
                                        jsonObject.addProperty("isMinimized", 1);
                                }
                                jsonObject.addProperty("img",
                                                urlFrank + "frank_04.gif");
                                jsonObject.addProperty("title", "¿Para qué sirven los contenedores?");
                                jsonObject.addProperty("desc",
                                                "¡Estos contenedores servirán para tirar toda la basura recolectada! <br><br>"
                                                                + "<div class='flex'> "
                                                                + "<section class='contentGif flex'>"
                                                                + "<img class='gif' src='tutorial/png/basurero_contenedor.png?v1' style='width: 200px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%); background: rgb(0 0 0 / 36%);'/> "
                                                                + "</section> "
                                                                + "</div>"
                                                                + "Te mostraremos como puedes recolectar bolsas de basura... <br><br>");
                                jsonArray.add(jsonObject);

                                jsonObject = new JsonObject();
                                jsonObject.addProperty("url_image", urlBase);
                                jsonObject.addProperty("informal", true);
                                jsonObject.addProperty("img",
                                                urlFrank + "frank_04.gif");
                                jsonObject.addProperty("title", "¡Acercate a los botes de basuras y recogelos!");
                                jsonObject.addProperty("desc",
                                                "Una vez estes cerca, presionalos dos veces para recogerlos. <br><br>"
                                                                + "<b>Como hacerlo:</b><br>"
                                                                + "<div class='flex'> "
                                                                + "<section class='contentGif flex'>"
                                                                + "<img class='gif' src='tutorial/png/action_point.png?v1' style='width: 50px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%); background: rgb(0 0 0 / 36%);'/> "
                                                                + "<img class='gif' src='tutorial/png/basurero_recoger.png?v1' style='width: 150px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%); background: rgb(0 0 0 / 36%);'/> "
                                                                + "</section> "
                                                                + "</div>"

                                                                + "");
                                jsonArray.add(jsonObject);
                                break;
                        case "1.2.2":
                                jsonObject = new JsonObject();
                                jsonObject.addProperty("url_image", urlBase);
                                jsonObject.addProperty("informal", true);
                                if (forceVisible) {
                                        jsonObject.addProperty("isMinimized", 0);
                                } else {
                                        jsonObject.addProperty("isMinimized", 1);
                                }
                                jsonObject.addProperty("img",
                                                urlFrank + "frank_04.gif");
                                jsonObject.addProperty("title", "¡Explora la ciudad y recoge los botes de basura!");
                                jsonObject.addProperty("desc",
                                                "Una vez estes cerca, presionalos dos veces para recogerlos. <br><br>"
                                                                + "<b>¡Debes acumular 20 bolsas de basuras!</b><br>"
                                                                + "<div class='flex'> "
                                                                + "<section class='contentGif flex'>"
                                                                + "<img class='gif' src='tutorial/png/action_point.png?v1' style='width: 50px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%); background: rgb(0 0 0 / 36%);'/> "
                                                                + "<img class='gif' src='tutorial/png/basurero_recoger.png?v1' style='width: 150px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%); background: rgb(0 0 0 / 36%);'/> "
                                                                + "</section> "
                                                                + "</div>"

                                                                + "<b>Recoge varios botes hasta acumular 20 basuras para continuar... </b>");
                                jsonArray.add(jsonObject);
                                break;
                        case "1.2.3":
                                jsonObject = new JsonObject();
                                jsonObject.addProperty("url_image", urlBase);
                                jsonObject.addProperty("informal", true);
                                if (forceVisible) {
                                        jsonObject.addProperty("isMinimized", 0);
                                } else {
                                        jsonObject.addProperty("isMinimized", 1);
                                }
                                jsonObject.addProperty("img",
                                                urlFrank + "frank_04.gif");
                                jsonObject.addProperty("title", "Acercate al contenedor y tira tu basura");
                                jsonObject.addProperty("desc",
                                                "¡Para recibir tu paga, deberas botar tu basura recolectada! es por ello que deberás acercarte al contenedor y presionarlo dos veces para tirar toda tu basura.<br><br>"
                                                                + "<div class='flex'> "
                                                                + "<section class='contentGif flex'>"
                                                                + "<img class='gif' src='tutorial/png/basurero_contenedor.png?v1' style='width: 200px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%); background: rgb(0 0 0 / 36%);'/> "
                                                                + "</section> "
                                                                + "</div>"
                                                                + "<b>Realiza esta acción para continuar...</b> <br><br>");
                                jsonArray.add(jsonObject);
                                break;
                        case "1.2.4":
                                jsonObject = new JsonObject();
                                jsonObject.addProperty("url_image", urlBase);
                                jsonObject.addProperty("informal", true);
                                if (forceVisible) {
                                        jsonObject.addProperty("isMinimized", 0);
                                } else {
                                        jsonObject.addProperty("isMinimized", 1);
                                }
                                jsonObject.addProperty("img",
                                                urlFrank + "frank_04.gif");
                                jsonObject.addProperty("title", "¡Abre el mapa y visita la casa de algun ciudadano!");
                                jsonObject.addProperty("desc",
                                                "¡Ahora abre el mapa, busca las habitaciones y visita la sala de algun ciudadano para recoger su basura recolectada...<br><br>"
                                                                + "<div class='flex'> "
                                                                + "<section class='contentGif flex'>"
                                                                + "<img class='gif' src='tutorial/png/mapa_habitaciones.png?v1' style='width: 250px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%); background: rgb(0 0 0 / 36%);'/> "
                                                                + "<img class='gif' src='tutorial/png/basura_casa.png?v1' style='width: 150px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%); background: rgb(0 0 0 / 36%);'/> "
                                                                + "<img class='gif' src='tutorial/png/action_point.png?v1' style='width: 50px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%); background: rgb(0 0 0 / 36%);'/> "
                                                                + "</section> "
                                                                + "</div>"
                                                                + "<b>Una vez llegues a la casa, ponte cerca del bote y presionalo dos veces...<br></b>Realiza esta acción para continuar... <br><br>");
                                jsonArray.add(jsonObject);
                                break;
                        case "1.3.0":
                                jsonObject = new JsonObject();
                                jsonObject.addProperty("event", "eventRecibirPasePolicial"); // Evento
                                jsonObject.addProperty("url_image", urlBase);
                                jsonObject.addProperty("informal", true);
                                if (forceVisible) {
                                        jsonObject.addProperty("isMinimized", 0);
                                } else {
                                        jsonObject.addProperty("isMinimized", 1);
                                }
                                jsonObject.addProperty("img",
                                                urlFrank + "frank_04.gif");
                                jsonObject.addProperty("title", "¡Activa tu empleo de policia!");
                                jsonObject.addProperty("desc",
                                                "<img class='gif' src='tutorial/png/police_active.png?v1' style='width: 70px;'/>"
                                                                + "<br> ¡Te entregaremos el permiso para que puedas acceder a la zona policial! (DEBES SER NIVEL 2)<br><br>"
                                                                + "<br> Una vez tengas el acceso deberas dirigirte al centro penitenciario y ponerte encima del escaner para activar tu empleo..."
                                                                + "<b>¡Recibe el pase ahora!</b><br><br>");
                                jsonArray.add(jsonObject);
                                break;
                        case "1.3.1":
                                jsonObject = new JsonObject();
                                jsonObject.addProperty("url_image", urlBase);
                                jsonObject.addProperty("informal", true);
                                if (forceVisible) {
                                        jsonObject.addProperty("isMinimized", 0);
                                } else {
                                        jsonObject.addProperty("isMinimized", 1);
                                }
                                jsonObject.addProperty("img",
                                                urlFrank + "frank_04.gif");
                                jsonObject.addProperty("title", "¡Activa tu empleo de policia!");
                                jsonObject.addProperty("desc",
                                                ""
                                                                + "<br> ¡Ya que tienes el pase de policia, acercate al escaner para activar tu empleo!<br><br>"
                                                                + ""
                                                                + "<div class='flex'> "
                                                                + "<section class='contentGif flex'>"
                                                                + "<img class='gif' src='tutorial/png/action_point.png?v1' style='width: 50px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%); background: rgb(0 0 0 / 36%);'/> "
                                                                + "<img class='gif' src='tutorial/png/scanner.png?v1' style='width: 70px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%); background: rgb(0 0 0 / 36%);'/> "
                                                                + "</section> "
                                                                + "</div>"
                                                                + "<b>¡Activa tu empleo ahora para continuar!</b><br><br>");
                                jsonArray.add(jsonObject);

                                break;
                        case "1.3.2":
                                jsonObject = new JsonObject();
                                jsonObject.addProperty("url_image", urlBase);
                                jsonObject.addProperty("informal", true);
                                if (forceVisible) {
                                        jsonObject.addProperty("isMinimized", 0);
                                } else {
                                        jsonObject.addProperty("isMinimized", 1);
                                }
                                jsonObject.addProperty("img",
                                                urlFrank + "frank_04.gif");
                                jsonObject.addProperty("title", "Funciones del policia");
                                jsonObject.addProperty("desc",
                                                ""
                                                                + "¡Tu labor como policía sera mantener el orden ante el caos!<br><br>"
                                                                + "<div class='flex'> "
                                                                + "<section class='contentGif'>"
                                                                + "<img class='gif' src='tutorial/png/mapa1.png?v1' style='width: 60px;border-radius: 12px;border: 2px solid rgba(0, 0, 0, 0.36);background: rgb(0 0 0 / 67%);height: 65px;'/> <br>"
                                                                +
                                                                "<b>Patrulla las calles: </b><br><br>"
                                                                + "<span>-Dirigete a las distintas salas del hotel y manten seguros a todos los usuarios.</span>"
                                                                + "</section> "
                                                                + "<section class='contentGif'>"
                                                                + "<img class='gif' src='items/arma_policial.png?v1' style='width: 60px;border-radius: 12px;border: 2px solid rgba(0, 0, 0, 0.36);background: rgb(0 0 0 / 67%);height: 65px;'/> <br><br>"
                                                                +
                                                                "<b>Utiliza tu arma: </b><br><br>"
                                                                + "<span>- Puedes presionar a los usuarios para ver su expediente, que te mostrará si tiene delitos pendientes por pagar.</span><br><br>"
                                                                + "<span>- Puedes equipar tu arma y presionar a un usuario para enviarlo a prisión.</span><br>"

                                                                + "</section> "

                                                                + "<section class='contentGif'>"
                                                                + "<img class='gif' src='tutorial/png/reporte_police.png?v1' style='width: 60px;border-radius: 12px;border: 2px solid rgba(0, 0, 0, 0.36);background: rgb(0 0 0 / 67%);height: 65px;'/> <br><br>"
                                                                +
                                                                "<b>Realiza reportes: </b><br><br>"
                                                                + "<span>- Cada vez que envies a prisión a alguién deberás hacer un reportaje en la cartelera para poder volver a habilitar tu arma.</span><br>"

                                                                + "</section> "
                                                                + "</div>"
                                                                + "<br><br>Ahora que sabes esto <b>¡puedes continuar!</b><br><br>");
                                jsonArray.add(jsonObject);

                                jsonObject = new JsonObject();
                                jsonObject.addProperty("url_image", urlBase);
                                jsonObject.addProperty("informal", true);
                                if (forceVisible) {
                                        jsonObject.addProperty("isMinimized", 0);
                                } else {
                                        jsonObject.addProperty("isMinimized", 1);
                                }
                                jsonObject.addProperty("img",
                                                urlFrank + "frank_04.gif");
                                jsonObject.addProperty("title", "¡Mira el expediente de algún usuario!");
                                jsonObject.addProperty("desc",
                                                "<span>¡Para completar esta parte del reto, deberás buscar a algún usuario de la ciudad y ver su expediente!</b>...</span><br>"

                                                                + "<div class='flex'> "
                                                                + "<section class='contentGif'>"
                                                                + "<img class='gif' src='tutorial/png/expediente.png?v1' style='width: 60px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%); background: rgb(0 0 0 / 36%);'/>"
                                                                + "</section> "
                                                                + "</div>"
                                                                + "<span>Una vez lo hagas, podrás continuar...</span>");
                                jsonArray.add(jsonObject);

                                break;
                        case "1.3.3":
                                jsonObject = new JsonObject();
                                jsonObject.addProperty("url_image", urlBase);
                                jsonObject.addProperty("informal", true);
                                if (forceVisible) {
                                        jsonObject.addProperty("isMinimized", 0);
                                } else {
                                        jsonObject.addProperty("isMinimized", 1);
                                }
                                jsonObject.addProperty("img",
                                                urlFrank + "frank_04.gif");
                                jsonObject.addProperty("title", "¡Arresta a algún usuario!");
                                jsonObject.addProperty("desc",
                                                "<span>¡Para completar esta parte del reto, deberás buscar a algún usuario de la ciudad que tenga delitos por pagar y arrestarlo!</b>...</span><br>"
                                                                + "<div class='flex'> "
                                                                + "<section class='contentGif'>"
                                                                + "<img class='gif' src='tutorial/png/arresto_true.png?v1' style='width: 60px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%); background: rgb(0 0 0 / 36%);'/>"
                                                                + "</section> "
                                                                + "</div>"
                                                                + "<span>Una vez lo hagas, podrás continuar...</span>");
                                jsonArray.add(jsonObject);
                                break;
                        case "1.4.0":
                                jsonObject = new JsonObject();
                                jsonObject.addProperty("url_image", urlBase);
                                jsonObject.addProperty("informal", true);
                                if (forceVisible) {
                                        jsonObject.addProperty("isMinimized", 0);
                                } else {
                                        jsonObject.addProperty("isMinimized", 1);
                                }
                                jsonObject.addProperty("img",
                                                urlFrank + "frank_04.gif");
                                jsonObject.addProperty("title", "¡Activa tu empleo de cafetero!");
                                jsonObject.addProperty("desc",
                                                ""
                                                                + "<br> ¡Acercate al escaner en la cafeteria para activar tu empleo!<br><br>"
                                                                + ""
                                                                + "<div class='flex'> "
                                                                + "<section class='contentGif flex'>"
                                                                + "<img class='gif' src='tutorial/png/action_point.png?v1' style='width: 50px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%); background: rgb(0 0 0 / 36%);'/> "
                                                                + "<img class='gif' src='tutorial/png/scanner.png?v1' style='width: 70px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%); background: rgb(0 0 0 / 36%);'/> "
                                                                + "</section> "
                                                                + "</div>"
                                                                + "<b>¡Activa tu empleo de cafetero ahora para continuar!</b><br><br>");
                                jsonArray.add(jsonObject);
                                break;
                        case "1.4.1":
                                jsonObject = new JsonObject();
                                jsonObject.addProperty("url_image", urlBase);
                                jsonObject.addProperty("informal", true);
                                if (forceVisible) {
                                        jsonObject.addProperty("isMinimized", 0);
                                } else {
                                        jsonObject.addProperty("isMinimized", 1);
                                }
                                jsonObject.addProperty("img",
                                                urlFrank + "frank_04.gif");
                                jsonObject.addProperty("title", "¡Muele granos de café para continuar!");
                                jsonObject.addProperty("desc",
                                                ""
                                                                + "<br> ¡Presiona el saco de granos y presiona el botón de tomar granos de café!<br><br> Una vez lo hagas, la acción te indicará lo siguiente que deberás hacer...<br><br>"
                                                                + ""
                                                                + "<div class='flex'> "
                                                                + "<section class='contentGif flex'>"
                                                                + "<img class='gif' src='tutorial/png/action_point.png?v1' style='width: 50px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%); background: rgb(0 0 0 / 36%);'/> "
                                                                + "<img class='gif' src='tutorial/png/granos_cafe.png?v1' style='width: 70px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%); background: rgb(0 0 0 / 36%);'/> "
                                                                + "</section> "
                                                                + "</div>"
                                                                + "<b>¡Hazlo ahora para continuar!</b><br><br>");
                                jsonArray.add(jsonObject);
                                break;
                        case "1.4.2":
                                jsonObject = new JsonObject();
                                jsonObject.addProperty("url_image", urlBase);
                                jsonObject.addProperty("informal", true);
                                if (forceVisible) {
                                        jsonObject.addProperty("isMinimized", 0);
                                } else {
                                        jsonObject.addProperty("isMinimized", 1);
                                }
                                jsonObject.addProperty("img",
                                                urlFrank + "frank_04.gif");
                                jsonObject.addProperty("title", "¡Prepara tus cafe en la maquina!");
                                jsonObject.addProperty("desc",
                                                ""
                                                                + "<br> ¡Presiona la maquina de cafe y presiona el botón de preparar cafe!<br><br>"
                                                                + ""
                                                                + "<div class='flex'> "
                                                                + "<section class='contentGif flex'>"
                                                                + "<img class='gif' src='tutorial/png/action_point.png?v1' style='width: 50px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%); background: rgb(0 0 0 / 36%);'/> "
                                                                + "<img class='gif' src='tutorial/png/maquinaCafe.png?v1' style='width: 70px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%); background: rgb(0 0 0 / 36%);'/> "
                                                                + "</section> "
                                                                + "</div>"
                                                                + "<b>¡Hazlo ahora para continuar!</b><br><br>");
                                jsonArray.add(jsonObject);
                                break;
                        case "1.4.3":
                                jsonObject = new JsonObject();
                                jsonObject.addProperty("url_image", urlBase);
                                jsonObject.addProperty("informal", true);
                                if (forceVisible) {
                                        jsonObject.addProperty("isMinimized", 0);
                                } else {
                                        jsonObject.addProperty("isMinimized", 1);
                                }
                                jsonObject.addProperty("img",
                                                urlFrank + "frank_04.gif");
                                jsonObject.addProperty("title", "¡Coloca los café en la estantería!");
                                jsonObject.addProperty("desc",
                                                ""
                                                                + "<br> ¡Presiona la estantería y presiona el botón de colocar cafe!<br><br>"
                                                                + ""
                                                                + "<div class='flex'> "
                                                                + "<section class='contentGif flex'>"
                                                                + "<img class='gif' src='tutorial/png/action_point.png?v1' style='width: 50px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%); background: rgb(0 0 0 / 36%);'/> "
                                                                + "<img class='gif' src='tutorial/png/estanteria.png?v1' style='width: 70px;border-radius: 12px;border: 2px solid rgb(0 0 0 / 36%); background: rgb(0 0 0 / 36%);'/> "
                                                                + "</section> "
                                                                + "</div>"
                                                                + "<b>¡Hazlo ahora para continuar!</b><br><br>");
                                jsonArray.add(jsonObject);
                                break;
                        default:
                                break;
                }

                return jsonArray;
        }

        public static void incomingListParteEmpleos(THashMap<String, Object> data, HabboClient wsclient)
                        throws SQLException {
                new IncomingListEmpleos(data, wsclient);
        }
}
