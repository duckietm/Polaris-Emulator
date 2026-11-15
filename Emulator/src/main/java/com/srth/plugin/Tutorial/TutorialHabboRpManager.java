package com.srth.plugin.Tutorial;

import java.sql.SQLException;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Consola.TypeMessageConsola;
import com.srth.plugin.Rooms.Mapa.Taxi.ControllerRecorridosTaxi;
import com.srth.plugin.Rooms.Mapa.Taxi.RecorridoTaxi;
import com.srth.plugin.Tutorial.eventos.EventosTutorial;
import com.srth.plugin.Tutorial.eventos.ActionEvents.ActionAddIngredientesHabbo;
import com.srth.plugin.Tutorial.eventos.ActionEvents.ActionCreateHome;
import com.srth.plugin.Tutorial.partes.*;
import com.srth.plugin.Tutorial.partes_delitos.PartesDelitosTutorial;
import com.srth.plugin.Tutorial.partes_empleos.PartesEmpleosTutorial;
import com.srth.plugin.Tutorial.partes_informal.PartesInformalArrays;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.Jobs.Job;
import com.srth.plugin.habbo.Jobs.ManagerButtonsRight;
import com.srth.plugin.websocket.Habbo.HabboClient;

import gnu.trove.map.hash.THashMap;

public class TutorialHabboRpManager {

        public int parte_tutorial = 0;
        public boolean forceVisible = true;
        private THashMap<Integer, JsonArray> partesTutorial = new THashMap<>();

        EventosTutorial eventosTutorial = new EventosTutorial();

        public TutorialHabboRpManager() {

        }

        public void loadPartesTutorial() {
                this.partesTutorial.clear();

                new ParteTutorial0();
                new ParteTutorial1();
                new ParteTutorial2();
                new ParteTutorial3();
                new ParteTutorial4();
                new ParteTutorial5();
                new ParteTutorial6();
                new ParteTutorial7();
                new ParteTutorial8();
                new ParteTutorial9();
                new ParteTutorial10();
                new ParteTutorial11();
                new ParteTutorial12();
                new ParteTutorial13();
                new ParteTutorial14();
                new ParteTutorial15();
                new ParteTutorial16();
                new ParteTutorial17();
                new ParteTutorial18();
                new ParteTutorial19();
        }

        public void registerParteTutorial(int parte, JsonArray array) {
                this.partesTutorial.put(parte, array);
        }

        public void sendHospital(Habbo habbo) {
                try {
                        Job hospital;
                        hospital = RoleplayWebsocketsRoomsInfo.getJobsManager().getJob("medico");
                        if (hospital.getRoomId() != 0) {
                                if (habbo.getHabboInfo().getCurrentRoom() == null) {
                                        return;
                                }
                                if (habbo.getHabboInfo().getCurrentRoom().getId() == hospital.getRoomId()) {
                                        return;
                                }
                                habbo.goToRoom(hospital.getRoomId());
                        }
                } catch (SQLException e) {
                        e.printStackTrace();
                }
        }

        public JsonArray getArray(Habbo habbo, Boolean forceVisible) {

                this.loadPartesTutorial();
                int getParteTutorial = this.parte_tutorial;

                try {
                        HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
                        getParteTutorial = habboRp.getHabboTutorial().getParteTutorial();
                } catch (SQLException e) {
                        e.printStackTrace();
                }

                this.forceVisible = forceVisible;

                return this.partesTutorial.get(getParteTutorial);
        }

        public JsonArray getArrayPersonalizado(Habbo habbo, int type) {
                JsonArray getPartesInformal = new PartesInformalArrays().getPartes(type);
                return getPartesInformal;
        }

        public JsonArray getArrayTutorialEmpleo(Habbo habbo, String type, Boolean forceVisible) {
                JsonArray getPartesInformal = new PartesEmpleosTutorial().getPartes(type, forceVisible);
                return getPartesInformal;
        }

        public JsonArray getArrayTutorialDelitos(Habbo habbo, String type, Boolean forceVisible) {
                JsonArray getPartesInformal = new PartesDelitosTutorial().getPartes(type, forceVisible);
                return getPartesInformal;
        }

        public void userEnterRoom(Habbo habbo) {

                try {

                        Emulator.getThreading().run(() -> {
                                RoleplayWebsocketsRoomsInfo.getRetosTutorialManager().load();
                        }, 1000);

                        HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
                        HabboTutorial habboTutorial = habboRp.getHabboTutorial();

                        if (habboRp.getConstructorSalas() == false) {
                                Emulator.getThreading().run(() -> {
                                        if (habboTutorial.getParteTutorial() <= 1 && !habboTutorial.getCompleted()) {
                                                RecorridoTaxi recorrido = RoleplayWebsocketsRoomsInfo
                                                                .getControllerRecorridosTaxi()
                                                                .getRecorridoTaxi(habbo);
                                                if (recorrido == null) {
                                                        this.sendHospital(habbo);

                                                        try {
                                                                Job hospital = RoleplayWebsocketsRoomsInfo
                                                                                .getJobsManager().getJob("medico");
                                                                if (hospital.getRoomId() != 0 && habbo.getHabboInfo()
                                                                                .getCurrentRoom() != null
                                                                                && habbo.getHabboInfo().getCurrentRoom()
                                                                                                .getId() == hospital
                                                                                                                .getRoomId()) {
                                                                        // ENVIAR ALERTA A TODOS
                                                                        RoleplayWebsocketsRoomsInfo.getConsolaManager()
                                                                                        .sendMessageConsolaGlobal(
                                                                                                        TypeMessageConsola
                                                                                                                        .getFormattedMessage(
                                                                                                                                        TypeMessageConsola.DEFAULT_MSG,
                                                                                                                                        habbo,
                                                                                                                                        "Entró por primera vez al roleplay. "));
                                                                }
                                                        } catch (Exception e) {
                                                        }
                                                }
                                        }
                                }, 1000);
                        }

                        Emulator.getThreading().run(() -> {
                                if (habboTutorial.getParteTutorial() >= 18 && habboTutorial.getCompleted() == true) {
                                        try {
                                                // Mostrar bottones
                                                new ManagerButtonsRight().ViewButtons(habbo);
                                        } catch (Exception e) {
                                        }
                                }
                        }, 1000);

                        Emulator.getThreading().run(() -> {
                                RecorridoTaxi recorrido = RoleplayWebsocketsRoomsInfo
                                                .getControllerRecorridosTaxi()
                                                .getRecorridoTaxi(habbo);
                                if (recorrido == null) {

                                        // Controlador de vista del roleplay
                                        habboTutorial.show(habboRp.getHabbo(), true);

                                        Room room = habbo.getHabboInfo().getCurrentRoom();
                                        if (room == null) {
                                                return;
                                        }

                                        if (habboTutorial.getCompleted() == false) {

                                                // Generate efecto sim
                                                room.giveEffect(habboRp.getHabbo(), Integer.valueOf(44), -1);
                                        }
                                } else {
                                        // Cuando tienes un recorrido activo
                                        if (habboTutorial.getParteTutorial() == 15) {

                                                habboTutorial.ocult(habbo);

                                                Room room = habbo.getHabboInfo().getCurrentRoom();
                                                if (room == null) {
                                                        return;
                                                }
                                                if (room.getName().contains("puente")
                                                                || room.getName().contains("penitenciario")
                                                                || room.getName().contains("lago")) {
                                                        Emulator.getThreading().run(() -> {
                                                                habboTutorial.ocult(habbo);
                                                        }, 100);
                                                } else {
                                                        Emulator.getThreading().run(() -> {
                                                                habboTutorial.show(habbo, true);
                                                        }, 100);
                                                }
                                        }
                                }

                        }, 1000);

                        if (habboTutorial.getParteTutorial() == 1 && habboTutorial.getCompleted() == false) {

                                Emulator.getThreading().run(() -> {

                                        RecorridoTaxi recorrido = RoleplayWebsocketsRoomsInfo
                                                        .getControllerRecorridosTaxi()
                                                        .getRecorridoTaxi(habbo);
                                        if (recorrido == null) {
                                                return;
                                        }
                                        Room room = habbo.getHabboInfo().getCurrentRoom();
                                        if (room == null) {
                                                return;
                                        }

                                        if (room.getId() == recorrido.getRoomMapa("calle_del_lago")) {
                                                habboTutorial.showPersonalizado(habbo, 0);
                                        }

                                        if (room.getId() == recorrido.getRoomMapa("centro_penitenciario")) {

                                                habboTutorial.incrementParteTutorial(habbo);
                                                habboTutorial.ocult(habbo);

                                                Emulator.getThreading().run(() -> {
                                                        habboTutorial.show(habbo, true);
                                                }, 3000);

                                                Emulator.getThreading().run(() -> {
                                                        habboRp.allowMove(false);
                                                }, 3000);
                                        }
                                }, 1000);
                        }
                        if (habboTutorial.getParteTutorial() == 3 && habboTutorial.getCompleted() == false) {

                                Emulator.getThreading().run(() -> {

                                        RecorridoTaxi recorrido = RoleplayWebsocketsRoomsInfo
                                                        .getControllerRecorridosTaxi()
                                                        .getRecorridoTaxi(habbo);
                                        if (recorrido == null) {
                                                return;
                                        }

                                        Room room = habbo.getHabboInfo().getCurrentRoom();
                                        if (room == null) {
                                                return;
                                        }

                                        if (room.getId() == recorrido.getRoomMapa("calles_peligrosas_3")) {
                                                habboTutorial.showPersonalizado(habbo, 1);
                                        }

                                        // FINAL RECORRIDO
                                        if (room.getId() == recorrido.getRoomMapa("supermercado")) {
                                                habboTutorial.incrementParteTutorial(habbo);
                                                habboTutorial.ocult(habbo);

                                                Emulator.getThreading().run(() -> {
                                                        habboTutorial.show(habbo, true);
                                                }, 3000);

                                                Emulator.getThreading().run(() -> {
                                                        habboRp.allowMove(false);
                                                }, 3000);
                                        }
                                }, 1000);
                        }

                        if (habboTutorial.getParteTutorial() == 5) {

                                Emulator.getThreading().run(() -> {
                                        int roomHomeId = habboRp.getInt("global.home.room.rp.id");
                                        Room room = habbo.getHabboInfo().getCurrentRoom();
                                        if (room.getId() == roomHomeId) {
                                                RoleplayWebsocketsRoomsInfo.getTutorialHabboRpManager().getEventos()
                                                                .eventCreateHomeRoleplay(habbo);
                                        }
                                }, 1000);

                        }
                        if (habboTutorial.getParteTutorial() == 15) {

                                habboTutorial.ocult(habbo);

                                Emulator.getThreading().run(() -> {

                                        habboTutorial.show(habbo, true);

                                        ControllerRecorridosTaxi controllerRecorridos = RoleplayWebsocketsRoomsInfo
                                                        .getControllerRecorridosTaxi();

                                        Room room = habbo.getHabboInfo().getCurrentRoom();
                                        if (room == null) {
                                                return;
                                        }

                                        // Cuando entres al hospital
                                        if (room.getId() == controllerRecorridos.getRoomMapa("hospital")) {

                                                habboTutorial.ocult(habbo);

                                                habboTutorial.incrementParteTutorial(habbo);
                                                habboRp.givePoints(10);

                                                Emulator.getThreading().run(() -> {
                                                        habboRp.showMessage(
                                                                        "RoleplayWebsocketsRoomsInfo/cash/0.png?v=1",
                                                                        "Te hemos regalado 10 cash para que puedas comprar una caña de pescar..."
                                                                                        .replace("%br%", "\n"));
                                                }, 2000);

                                                Emulator.getThreading().run(() -> {
                                                        habboTutorial.show(habbo, true);
                                                }, 3000);
                                        }

                                }, 1000);
                        }

                        if (habboTutorial.getParteTutorial() == 17) {

                                Emulator.getThreading().run(() -> {

                                        ControllerRecorridosTaxi controllerRecorridos = RoleplayWebsocketsRoomsInfo
                                                        .getControllerRecorridosTaxi();
                                        Room room = habbo.getHabboInfo().getCurrentRoom();
                                        if (room == null) {
                                                return;
                                        }

                                        // Cuando entres a la calle del lago
                                        if (room.getId() == controllerRecorridos.getRoomMapa("calle_del_lago")) {
                                                habboTutorial.incrementParteTutorial(habbo);

                                                habboTutorial.ocult(habbo);

                                                Emulator.getThreading().run(() -> {
                                                        habboTutorial.show(habbo, true);
                                                }, 3000);
                                        }

                                }, 1000);
                        }
                } catch (SQLException e) {
                        e.printStackTrace();
                }
        }

        public void incoming(THashMap<String, Object> data, HabboClient wsclient) throws SQLException {

                String get_action = ((String) data.get("action"));

                HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(wsclient.getHabbo());
                HabboTutorial habboTutorial = habboRp.getHabboTutorial();

                if (get_action.equalsIgnoreCase("incrementParteTutorialInicio")) {
                        if (habboTutorial.getParteTutorial() == 0) {
                                habboTutorial.incrementParteTutorial(wsclient.getHabbo());
                                habboTutorial.show(wsclient.getHabbo(), true);
                        }
                }

                if (get_action.equalsIgnoreCase("incrementParteTutorial")) {

                        habboTutorial.incrementParteTutorial(wsclient.getHabbo());

                        if (habboTutorial.getParteTutorial() == 3) {

                                habboTutorial.ocult(wsclient.getHabbo());

                                Emulator.getThreading().run(() -> {
                                        habboTutorial.show(wsclient.getHabbo(), true);
                                }, 5000);
                        } else {

                                Emulator.getThreading().run(() -> {
                                        habboTutorial.show(wsclient.getHabbo(), true);
                                }, 1000);

                                habboTutorial.ocult(wsclient.getHabbo());
                        }
                }

                if (get_action.equalsIgnoreCase("GoToRoomRecorridoCentroPenintenciario")) {

                        // habboRp.getHabboTutorial().incrementParteTutorial(wsclient.getHabbo());
                        habboRp.getHabboTutorial().ocult(wsclient.getHabbo());

                        if (RoleplayWebsocketsRoomsInfo.getRoomsRoleplayManager().getRoom("centro_penitenciario")
                                        .getRoomId() != 0) {

                                ControllerRecorridosTaxi controllerRecorridosTaxi = RoleplayWebsocketsRoomsInfo
                                                .getControllerRecorridosTaxi();
                                RecorridoTaxi recorrido = controllerRecorridosTaxi
                                                .getRecorridoTaxi(wsclient.getHabbo());
                                if (recorrido == null) {

                                        habboRp.allowMove(true);

                                        controllerRecorridosTaxi.setRecorridoTaxi(wsclient.getHabbo(),
                                                        RoleplayWebsocketsRoomsInfo.getRoomsRoleplayManager()
                                                                        .getRoom("centro_penitenciario").getRoomId());
                                }

                        } else {
                                wsclient.getHabbo().whisper("No hay centro penintenciario para visitar");
                        }
                }

                if (get_action.equalsIgnoreCase("GoToRoomRecorridoSupermercado")) {

                        habboRp.getHabboTutorial().ocult(wsclient.getHabbo());

                        // Si exise un supermercado
                        if (RoleplayWebsocketsRoomsInfo.getRoomsRoleplayManager().getRoom("supermercado")
                                        .getRoomId() != 0) {

                                ControllerRecorridosTaxi controllerRecorridosTaxi = RoleplayWebsocketsRoomsInfo
                                                .getControllerRecorridosTaxi();
                                RecorridoTaxi recorrido = controllerRecorridosTaxi
                                                .getRecorridoTaxi(wsclient.getHabbo());
                                if (recorrido == null) {

                                        habboRp.allowMove(true);
                                        controllerRecorridosTaxi.setRecorridoTaxi(wsclient.getHabbo(),
                                                        RoleplayWebsocketsRoomsInfo.getRoomsRoleplayManager()
                                                                        .getRoom("supermercado").getRoomId());
                                }
                        } else {
                                wsclient.getHabbo().whisper("No hay supermercado para visitar");
                        }

                }

                if (get_action.equalsIgnoreCase("buyingIngredientesGoTo")) {

                        habboRp.getHabboTutorial().ocult(wsclient.getHabbo());
                        habboRp.allowMove(true);

                        habboRp.showMessage("RoleplayWebsocketsRoomsInfo/tutorial/ingredientes_variados.png?v=1",
                                        "Acercate al mostrador de Ingredientes variados y compra algunos..."
                                                        .replace("%br%", "\n"));

                        if (habboRp.checkSettings("giveCashIngredientesBuyTuto", 0) == 0) {

                                Emulator.getThreading().run(() -> {
                                        habboRp.givePoints(10);
                                }, 5000);

                                Emulator.getThreading().run(() -> {
                                        habboRp.showMessage("RoleplayWebsocketsRoomsInfo/cash/0.png?v=1",
                                                        "Te hemos regalado 10 cash para que puedas comprar ingredientes..."
                                                                        .replace("%br%", "\n"));
                                }, 8000);

                                habboRp.updateData("giveCashIngredientesBuyTuto", "1");
                        }
                }

                if (get_action.equalsIgnoreCase("processCrearHomeRoleplay")) {

                        habboRp.getHabboTutorial().ocult(wsclient.getHabbo());

                        // new ActionCreateHome(wsclient.getHabbo());

                        if (habboRp.getInt("global.home.room.rp.id") == 0) {
                                new ActionCreateHome(wsclient.getHabbo());
                        } else {
                                Emulator.getThreading().run(() -> {
                                        habboRp.showMessage("RoleplayWebsocketsRoomsInfo/works/casa.png?v=1",
                                                        "Tu casa ya fue creada anteriormente, te llevaremos a ella."
                                                                        .replace("%br%", "\n"));

                                }, 500);

                                Emulator.getThreading().run(() -> {
                                        ControllerRecorridosTaxi controllerRecorridosTaxi = RoleplayWebsocketsRoomsInfo
                                                        .getControllerRecorridosTaxi();
                                        RecorridoTaxi recorrido = controllerRecorridosTaxi
                                                        .getRecorridoTaxi(wsclient.getHabbo());
                                        if (recorrido == null) {
                                                controllerRecorridosTaxi.setRecorridoTaxi(wsclient.getHabbo(),
                                                                habboRp.getInt("global.home.room.rp.id"));
                                        }
                                }, 1000);
                        }
                }

                if (get_action.equalsIgnoreCase("proccesGiveIngredientesHorno")) {

                        Emulator.getThreading().run(() -> {
                                habboRp.getHabboTutorial().ocult(wsclient.getHabbo());
                                habboRp.getHabboTutorial().incrementParteTutorial(wsclient.getHabbo());
                        }, 2000);

                        Emulator.getThreading().run(() -> {
                                habboRp.getHabboTutorial().show(wsclient.getHabbo(), true);
                        }, 10000);

                        new ActionAddIngredientesHabbo(wsclient.getHabbo());
                }

                if (get_action.equalsIgnoreCase("showMapaRoleplayEvent")) {

                        Emulator.getThreading().run(() -> {
                                habboRp.getHabboTutorial().ocult(wsclient.getHabbo());

                                habboRp.showMessage("RoleplayWebsocketsRoomsInfo/actions/mapa1.png",
                                                "Abre ahora el mapa y dirigete al hospital para continuar con el tutorial...");
                        }, 500);

                        Emulator.getThreading().run(() -> {
                                try {
                                        new ManagerButtonsRight().ViewButtons(wsclient.getHabbo());
                                } catch (SQLException e) {
                                        e.printStackTrace();
                                }
                        }, 2000);
                }

                if (get_action.equalsIgnoreCase("finishTutorial")) {
                        this.eventosTutorial.eventFinishTutorial(wsclient.getHabbo());
                }

        }

        public JsonObject getButtonMoreTareas(Habbo habbo) {

                String url_hotel_base = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.url.hotel.base");
                String rutaImagesNotifications = RoleplayWebsocketsRoomsInfo.getConfig()
                                .getValue("global_url.hotel.notifications")
                                .replace("%url.hotel.base%", url_hotel_base) + "RoleplayWebsocketsRoomsInfo/";

                try {
                        HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

                        if (habboRp.getHabboTutorial().getParteTutorial() < 20) {
                                return null;
                        }

                        JsonObject jsonObject;
                        jsonObject = new JsonObject();
                        jsonObject.addProperty("title", "Tutoriales");
                        jsonObject.addProperty("img", rutaImagesNotifications + "/tareas/frank.gif");
                        jsonObject.addProperty("event", "viewRetosTutorial");
                        jsonObject.addProperty("notification", 0);

                        return jsonObject;

                } catch (Exception e) {
                }

                return null;
        }

        public EventosTutorial getEventos() {
                return eventosTutorial;
        }

}
