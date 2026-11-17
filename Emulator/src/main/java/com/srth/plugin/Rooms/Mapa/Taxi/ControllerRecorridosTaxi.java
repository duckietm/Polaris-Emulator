package com.srth.plugin.Rooms.Mapa.Taxi;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.websocket.communication.outgoing.messages.ShowConfirmAlert;
import gnu.trove.map.hash.THashMap;
import java.sql.SQLException;

public class ControllerRecorridosTaxi {

    private static final long TAXI_ROOM_ENTRY_RETRY_MS = 50L;
    private static final long TAXI_ROOM_ENTRY_TIMEOUT_NS = 10_000_000_000L;

    private THashMap<Habbo, RecorridoTaxi> habbo_goto = new THashMap<Habbo, RecorridoTaxi>();

    public THashMap<Habbo, RecorridoTaxi> getHabbosRecorridos() {
        return habbo_goto;
    }

    public RecorridoTaxi getRecorridoTaxi(Habbo habbo) {
        return habbo_goto.get(habbo);
    }

    public int getRoomMapa(String name) {
        return RoleplayWebsocketsRoomsInfo.getRoomsRoleplayManager()
                .getRoom(name)
                .getRoomId();
    }

    public RecorridoTaxi setRecorridoTaxi(Habbo habbo, int roomId) {
        return this.setRecorridoTaxi(habbo, roomId, false);
    }

    public RecorridoTaxi setPoliceTransportTaxi(Habbo habbo, int roomId) {
        return this.setRecorridoTaxi(habbo, roomId, true);
    }

    private RecorridoTaxi setRecorridoTaxi(Habbo habbo, int roomId, boolean pauseAtDestinationForPoliceTransport) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            if (habboRp.getHabboTutorial().getParteTutorial() == 15) {
                if (RoleplayWebsocketsRoomsInfo.getRoomsRoleplayManager()
                                .getRoom("hospital")
                                .getRoomId()
                        != roomId) {
                    habboRp.showMessage(
                            "RoleplayWebsocketsRoomsInfo/tutorial/casa.png",
                            "Estas intentando ir a una sala erronea, debes regresar al hospital para continuar con el tutorial...");
                    return null;
                }
            }
        } catch (Exception e) {
        }

        Room roomIdView = Emulator.getGameEnvironment().getRoomManager().loadRoom(roomId);
        if (roomIdView == null) {
            return null;
        }

        habbo.shout(RoleplayWebsocketsRoomsInfo.getTexts()
                .getValue("recorrido_msg_init_taxi")
                .replace("%room_id%", roomIdView.getId() + "")
                .replace("%room_name%", roomIdView.getName() + ""));

        boolean taxiBotRideMode = !pauseAtDestinationForPoliceTransport
                && RoleplayWebsocketsRoomsInfo.getConfig().getInt("enable_taxi_bot_mode", 1) == 1;
        RecorridoTaxi recorridoTaxi = new RecorridoTaxi(
                habbo, roomId, pauseAtDestinationForPoliceTransport, taxiBotRideMode, taxiBotRideMode);
        this.getHabbosRecorridos().put(habbo, recorridoTaxi);
        if (taxiBotRideMode && !TaxiBotRideManager.start(habbo, recorridoTaxi)) {
            this.removeRecorridoTaxi(habbo);
            habbo.whisper("No se pudo iniciar el taxi. Intenta solicitarlo de nuevo.", RoomChatMessageBubbles.NORMAL);
            return null;
        }

        return recorridoTaxi;
    }

    public void removeRecorridoTaxi(Habbo habbo) {
        RecorridoTaxi recorrido = this.getHabbosRecorridos().remove(habbo);
        if (recorrido != null) {
            recorrido.setStatus(false);
        }
    }

    public void userEnterRoom(Room room, Habbo habbo) {
        RecorridoTaxi taxiRoute = this.getRecorridoTaxi(habbo);
        if (taxiRoute != null && taxiRoute.isTaxiBotRideMode()) {
            long deadline = System.nanoTime() + TAXI_ROOM_ENTRY_TIMEOUT_NS;
            Emulator.getThreading()
                    .run(() -> completeTaxiBotRoomEntry(room, habbo, deadline), TAXI_ROOM_ENTRY_RETRY_MS);
        }

        Emulator.getThreading()
                .run(
                        () -> {
                            if (this.getHabbosRecorridos().get(habbo) != null) {

                                RecorridoTaxi recorridoTaxi =
                                        this.getHabbosRecorridos().get(habbo);
                                if (!recorridoTaxi.getStatus() || !recorridoTaxi.isStarted()) {
                                    return;
                                }

                                try {
                                    RecorridoTaxi recorrido = RoleplayWebsocketsRoomsInfo.getControllerRecorridosTaxi()
                                            .getRecorridoTaxi(habbo);
                                    if (recorrido == null) {
                                        return;
                                    }

                                    HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager()
                                            .getHabboRp(habbo);
                                    Room roomActual = habbo.getHabboInfo().getCurrentRoom();

                                    if (roomActual == null) {
                                        return;
                                    }

                                    // EJECUTA LA LOGICA SI O SI
                                    String flechaObjetivo = recorrido.getControllerUserEnterRoom(
                                            recorrido.getRoomObjetive(), roomActual);
                                    recorrido.changeFlechasObjetiveGoto(room, flechaObjetivo);

                                    // Por defecto 634 // Personalizado 750
                                    int enable_taxi = RoleplayWebsocketsRoomsInfo.getConfig()
                                            .getInt("enable_taxi_id");
                                    habboRp.setEnable(enable_taxi);
                                    roomActual.giveEffect(habbo, Integer.valueOf(enable_taxi), -1);

                                } catch (SQLException e) {
                                    e.printStackTrace();
                                }
                            }
                        },
                        1000);
    }

    private void completeTaxiBotRoomEntry(Room room, Habbo habbo, long deadline) {
        RecorridoTaxi recorrido = this.getRecorridoTaxi(habbo);
        if (recorrido == null || !recorrido.isTaxiBotRideMode() || habbo.getHabboInfo() == null) {
            return;
        }

        if (habbo.getHabboInfo().getCurrentRoom() != room) {
            if (System.nanoTime() < deadline) {
                Emulator.getThreading()
                        .run(() -> completeTaxiBotRoomEntry(room, habbo, deadline), TAXI_ROOM_ENTRY_RETRY_MS);
            }
            return;
        }

        if (recorrido.getStatus() && recorrido.isStarted()) {
            String flechaObjetivo = recorrido.getControllerUserEnterRoom(recorrido.getRoomObjetive(), room);
            recorrido.changeFlechasObjetiveGoto(room, flechaObjetivo);
        }
        TaxiBotRideManager.onUserEnterRoom(habbo, room);
    }

    public void eventUserMove(MessageHandler event) {

        Habbo habbo = event.client.getHabbo();
        RoomUnit roomUnit = habbo.getRoomUnit();

        RecorridoTaxi recorrido =
                RoleplayWebsocketsRoomsInfo.getControllerRecorridosTaxi().getRecorridoTaxi(habbo);
        if (recorrido != null) {

            String url_hotel_base = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.url.hotel.base");
            String global_url_images = RoleplayWebsocketsRoomsInfo.getConfig()
                    .getValue("global_url_images")
                    .replace("%url.hotel.base%", url_hotel_base);
            String url_images = global_url_images + "/items/";

            try {
                Room currentRoom = habbo.getHabboInfo().getCurrentRoom();
                if (currentRoom == null) {
                    return;
                }
                Room objectiveRoom = recorrido.getRoomObjetive();
                if (objectiveRoom != null && currentRoom.getId() == objectiveRoom.getId()) {
                    if (recorrido.isTaxiBotRideMode()) {
                        event.isCancelled = true;
                    }
                    return;
                }

                HabboRp habboRp =
                        RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

                if (!recorrido.isTaxiBotRideMode()
                        && !habboRp.getHabboTutorial().getCompleted()) {
                    return;
                }

                String estructura = "user_id:%user_id%;";
                String total_string =
                        estructura.replace("%user_id%", habbo.getHabboInfo().getId() + "");
                if (habboRp.checkSettings("global_confirm_data", total_string) != "none") {
                    habboRp.saveToDatabase(total_string, "global_confirm_data");
                }

                Boolean existConfirmacion = habboRp.getCacheInt("existConfirmacionExitRecorrido") == 1 ? true : false;
                if (existConfirmacion == false) {
                    habboRp.registerCache("existConfirmacionExitRecorrido", 1 + "");
                    // Mostrar confirmación
                    habboRp.getHabboClient()
                            .send(new ShowConfirmAlert("ShowConfirmAlert", "exit_recorrido", 1, 120, true, ""));
                }
            } catch (Exception e) {
            }
            event.isCancelled = true;
            return;
        }
    }

    public void eventFinaliceRecorrido(Habbo habbo) {
        RecorridoTaxi recorrido =
                RoleplayWebsocketsRoomsInfo.getControllerRecorridosTaxi().getRecorridoTaxi(habbo);
        if (recorrido != null) {
            if (recorrido.isTaxiBotRideMode()) {
                TaxiBotRideManager.cancel(habbo);
            }
            this.removeRecorridoTaxi(habbo);
            try {

                habbo.getRoomUnit().setGoalLocation(habbo.getRoomUnit().getCurrentLocation());

                HabboRp habboRp =
                        RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
                Room roomActual = habbo.getHabboInfo().getCurrentRoom();
                if (!recorrido.isTaxiBotRideMode()) {
                    if (roomActual != null) {
                        roomActual.giveEffect(habbo, Integer.valueOf(0), -1);
                    }
                    habboRp.setEnable(0);
                }

                habbo.talk(RoleplayWebsocketsRoomsInfo.getTexts().getValue("recorrido_msg_finalice_taxi"));

                Boolean existConfirmacion = habboRp.getCacheInt("existConfirmacionExitRecorrido") == 1 ? true : false;
                habboRp.registerCache("existConfirmacionExitRecorrido", 0 + "");

            } catch (Exception e) {
            }
        }
    }

    public void finishTaxiBotRide(Habbo habbo) {
        TaxiBotRideManager.finishAtDestination(habbo);
    }

    public void guideTaxiBot(Habbo habbo, Room room, HabboItem exitArrow) {
        TaxiBotRideManager.guideRide(habbo, room, exitArrow);
    }

    public void completeRecorridoTaxi(Habbo habbo) {
        RecorridoTaxi recorrido = this.getRecorridoTaxi(habbo);
        if (recorrido == null) {
            return;
        }

        recorrido.setStatus(false);
        this.removeRecorridoTaxi(habbo);
        try {
            if (habbo.getRoomUnit() != null) {
                habbo.getRoomUnit().setGoalLocation(habbo.getRoomUnit().getCurrentLocation());
            }

            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            Room roomActual = habbo.getHabboInfo().getCurrentRoom();
            if (!recorrido.isTaxiBotRideMode()) {
                if (roomActual != null) {
                    roomActual.giveEffect(habbo, 0, -1);
                }
                habboRp.setEnable(0);
            }

            habbo.shout(RoleplayWebsocketsRoomsInfo.getTexts().getValue("recorrido_msg_finalice_taxi"));
            habboRp.registerCache("existConfirmacionExitRecorrido", "0");
        } catch (Exception e) {
            RoleplayWebsocketsRoomsInfo.utilities().LOGGER("Error al finalizar el recorrido de taxi: " + e);
        }
    }

    public void removeCacheRecorrido(Habbo habbo) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            habboRp.registerCache("existConfirmacionExitRecorrido", 0 + "");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
