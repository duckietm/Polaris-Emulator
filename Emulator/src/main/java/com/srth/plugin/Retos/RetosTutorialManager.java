package com.srth.plugin.Retos;

import java.util.HashMap;
import java.util.Map;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.habboway.nux.NuxAlertComposer;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.communication.Outcoming;
import com.srth.plugin.websocket.communication.outgoing.messages.MessageManagerDoubleObjet;

import gnu.trove.map.hash.THashMap;

public class RetosTutorialManager {

    private final Map<Integer, CategoriaReto> categoriasMap = new HashMap<>();

    String url_hotel_base = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.url.hotel.base");
    String url_hotel_notifications = RoleplayWebsocketsRoomsInfo.getConfig()
            .getValue("global_url.hotel.notifications").replace("%url.hotel.base%", url_hotel_base);
    String global_url_images = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url_images")
            .replace("%url.hotel.base%", url_hotel_base);
    String global_url_notifications = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url.hotel.notifications")
            .replace("%url.hotel.base%", url_hotel_base) + "RoleplayWebsocketsRoomsInfo/";

    public RetosTutorialManager() {
    }

    public void setHabbo(Habbo habbo) {
    }

    public void load() {

        this.categoriasMap.clear();

        int points_type = RoleplayWebsocketsRoomsInfo.getConfig().getInt("seasonal.rol.primary.type");

        // ------------------------------------------
        // --- CATEGORÍA 1: Consigue Empleo ---
        // ------------------------------------------
        CategoriaReto empleo = new CategoriaReto(1, "Consigue empleo",
                global_url_notifications + "job_medico/jeringa.png",
                "Es hora de que consigas un empleo formal");
        empleo.agregarTarea(1, empleo.getCategoria(), global_url_notifications + "job_medico/jeringa.png",
                global_url_notifications + "empleos/scanner_alto.png", "¡Consigue tu primer empleo como médico!",
                "¡Acercate al escanner dentro del hospital para activar el empleo! Gracias al empleo de médico, mantendrás con vida a todos los ciudadanos.",
                3,
                "activeRetoEmpleoMedico", 1, points_type);
        empleo.agregarTarea(2, empleo.getCategoria(), global_url_notifications + "job_aseo/rp_basurero_icon.png",
                global_url_notifications + "job_aseo/basurero.png", "¡Consigue empleo del aseo!",
                "¡Activa tus dotes limpiadores y deja pulidos los botes de basura de la ciudad y de las casas de los usuarios!",
                5,
                "activeRetoEmpleoAseo", 5, points_type);
        empleo.agregarTarea(3, empleo.getCategoria(), global_url_notifications + "job_polices/police.png",
                global_url_notifications + "job_polices/arresto_true.png", "¡Consigue empleo de policia!",
                "¡Logra mantener la paz y la estabilidad en las calles, lleva a prisión a todos los delincuentes! (Requiere nivel 2 de ciudadano)",
                5,
                "activeRetoEmpleoPolice", 4, points_type);
        empleo.agregarTarea(4, empleo.getCategoria(), global_url_notifications + "job_cafetero/work_active.png",
                global_url_notifications + "job_cafetero/maquina.png", "¡Consigue empleo de cafetero!",
                "¡Muele granos de café y prepara las mejores tazas de café para la venta!",
                4,
                "activeRetoEmpleoCafetero", 5, points_type);
        categoriasMap.put(1, empleo);

        // --------------------------------------------------
        // // --- CATEGORÍA 2: Conviértete en Pandillero ---
        // --------------------------------------------------
        CategoriaReto pandillero = new CategoriaReto(2, "Conviértete en malechor",
                global_url_images + "retos/malechores.png", "Activa las tareas de pandillero y domina las calles");
        pandillero.agregarTarea(1, pandillero.getCategoria(), global_url_images +
                "retos/llave.png",
                global_url_images + "retos/llave.png", "¡Golpea con una llave a alguién más!",
                "Activa este reto para enfrentarte a llaverazos con otra persona.", 1,
                "activeEventIniciacionPvpGolpes", 5, points_type);
        pandillero.agregarTarea(2, pandillero.getCategoria(), global_url_images +
                "retos/arma_ilegal.png",
                global_url_images + "retos/arma_ilegal.png", "Consigue un arma ilegal y asesina a alguien",
                "Activa este reto para obtener tu primer arma y comenzar delinquir", 3,
                "activeEventIniciacionPvpArmas", 5, points_type);
        pandillero.agregarTarea(3, pandillero.getCategoria(), global_url_notifications +
                "hack_cajeros/llave.png",
                global_url_notifications + "hack_cajeros/cajero_furni.png",
                "Roba cajeros automáticos con tu llave",
                "Activa este reto para obtener una llave que te permitirá robar cajeros automáticos", 2,
                "activeEventCajeroAutomaticoLlave", 2, points_type);
        categoriasMap.put(2, pandillero);

    }

    public Map<Integer, CategoriaReto> getCategorias() {
        return categoriasMap;
    }

    public CategoriaReto getCategoria(int id) {
        return categoriasMap.get(id);
    }

    public JsonArray getArray(Habbo habbo) {
        JsonArray buttonInfo = new JsonArray();
        JsonObject jsonObject;
        jsonObject = new JsonObject();
        jsonObject.addProperty("img", "/tareas/quest.png");
        jsonObject.addProperty("event", "viewTareasWindow");

        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

            jsonObject.addProperty("categoria_active", habboRp.getInt("tutorial_retos.categoria.active"));
            jsonObject.addProperty("reto_active", habboRp.getInt("tutorial_retos.reto.active"));

        } catch (Exception e) {
        }

        buttonInfo.add(jsonObject);
        return buttonInfo;
    }

    public JsonObject getObjetRetos(Habbo habbo) {
        JsonObject jsonObject = new JsonObject();
        JsonArray infoCategorias = new JsonArray();

        for (CategoriaReto categoria : RoleplayWebsocketsRoomsInfo.getRetosTutorialManager().getCategorias().values()) {
            JsonObject jsonObjectCategoria = new JsonObject();
            jsonObjectCategoria.addProperty("type", categoria.getCategoria());
            jsonObjectCategoria.addProperty("title", categoria.getTitle());
            jsonObjectCategoria.addProperty("img", categoria.getImg());
            jsonObjectCategoria.addProperty("desc", categoria.getDesc());

            try {

                HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
                if (habboRp.getHabboRetos().getCategoriaRetoActive() != null && habboRp.getHabboRetos()
                        .getCategoriaRetoActive() == categoria) {
                    jsonObjectCategoria.addProperty("active", 1);
                }

            } catch (Exception e) {
                // TODO: handle exception
            }

            // 1. Creamos el array donde irán todos los retos de ESTA categoría
            JsonArray listaRetos = new JsonArray();

            for (TareaCategoria retos : categoria.getRetos().values()) {

                String formatName = "tutorial_retos_%number_categoria%_%number_reto%"
                        .replace("%number_categoria%", categoria.getCategoria() + "")
                        .replace("%number_reto%", retos.getId() + "");

                JsonObject jsonObjectReto = new JsonObject();
                jsonObjectReto.addProperty("id", retos.getId());
                jsonObjectReto.addProperty("type", retos.getType());
                jsonObjectReto.addProperty("title", retos.getTitle());
                jsonObjectReto.addProperty("img", retos.getImg());
                jsonObjectReto.addProperty("img_secundary", retos.getImgSecundary());
                jsonObjectReto.addProperty("desc", retos.getDesc());
                jsonObjectReto.addProperty("points", retos.getPoints());
                jsonObjectReto.addProperty("points_type_image",
                        url_hotel_notifications + "RoleplayWebsocketsRoomsInfo/cash/" + retos.getPoinsType() + ".png");
                jsonObjectReto.addProperty("event", retos.getEvent());
                jsonObjectReto.addProperty("events_required", retos.getEventsRequired());

                // GUARDAR EN BASE DE DATOS
                try {
                    HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
                    habboRp.checkSettings(formatName, "0");

                    TareaCategoria retoActive = habboRp.getHabboRetos().getRetoActive();
                    if (retoActive != null
                            && retoActive.getId() == retos.getId()) {
                        jsonObjectReto.addProperty("active", 1);
                    } else {
                        jsonObjectReto.addProperty("active", 0);
                    }

                    if (habboRp.getInt(formatName) >= retos.getEventsRequired()) {
                        jsonObjectReto.addProperty("finalice", 1);
                    } else {
                        jsonObjectReto.addProperty("finalice", 0);
                    }

                    jsonObjectReto.addProperty("progreso", habboRp.checkSettings(formatName, "0"));
                } catch (Exception e) {
                }

                // 2. Añadimos el objeto del reto al array de retos
                listaRetos.add(jsonObjectReto);
            }

            // 3. Añadimos el array de retos al objeto de la categoría
            jsonObjectCategoria.add("retos", listaRetos);

            // 4. Añadimos la categoría completa al array principal
            infoCategorias.add(jsonObjectCategoria);
        }

        jsonObject.add("categorias", infoCategorias);
        return jsonObject;
    }

    public void showListRetos(Habbo habbo) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            habboRp.getHabboClient()
                    .send(new MessageManagerDoubleObjet("MessageManager", true, Outcoming.RetosTutorial,
                            this.getArray(habbo), this.getObjetRetos(habbo)));
        } catch (Exception e) {
        }
    }

    public void ocultListRetos(Habbo habbo) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            habboRp.getHabboClient()
                    .send(new MessageManagerDoubleObjet("MessageManager", false, Outcoming.RetosTutorial,
                            null, null));
        } catch (Exception e) {
        }
    }

    public void IncomingListActions(THashMap<String, Object> data, HabboClient wsclient) {
        if (wsclient != null) {

            String name = (String) data.get("name");

            if (name.equalsIgnoreCase("viewRetosTutorial")) {

                this.showListRetos(wsclient.getHabbo());

            } else if (name.equalsIgnoreCase("viewEmpleosWindows")) {

                RoleplayWebsocketsRoomsInfo.getJobsManager().showWindowJob(wsclient.getHabbo());

            }
        }
    }

    public void getIncomingActions(GameClient client, THashMap<String, Object> data) {
        try {

            // RoleplayWebsocketsRoomsInfo.utilities().LOGGER(data + " DATA");

            String name = ((String) data.get("name"));
            if (name.equalsIgnoreCase("activeRetosEvent")) {

                int type = (int) ((double) data.get("item"));
                String action = (String) data.get("action");
                int id = Integer.parseInt(action);

                this.eventActiveTutorial(client.getHabbo(), type, id);
            }

            if (name.equalsIgnoreCase("desactiveRetosEvent")) {
                this.eventDesactiveRetos(client.getHabbo());
            }

            if (name.equalsIgnoreCase("showVipMenu")) {
                client.getHabbo().getClient().sendResponse(new NuxAlertComposer("habboUI/open/hccenter"));
            }

        } catch (Exception e) {
        }
    }

    public void eventActiveTutorial(Habbo habbo, int type, int id) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

            habboRp.getHabboRetos().showRetoActive(type, id);
            habboRp.getHabboTutorial().setCompleted(true);

            this.ocultListRetos(habbo);
        } catch (Exception e) {
        }
    }

    public void eventDesactiveRetos(Habbo habbo) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            habboRp.getHabboRetos().removeRetoActive();

            habboRp.getHabboTutorial().ocultTutorialEmpleo(habbo);

            this.ocultListRetos(habbo);
            this.showListRetos(habbo);
            habboRp.updateData("tutorial_retos.categoria.active", "0");
            habboRp.updateData("tutorial_retos.reto.active", "0");

        } catch (Exception e) {
        }
    }

}
