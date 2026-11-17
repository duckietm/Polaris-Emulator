package com.srth.plugin.Rooms.Mapa.Taxi;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.ICycleable;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.websocket.Habbo.HabboClient;
import gnu.trove.map.hash.THashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;

public class RecorridoTaxi implements ICycleable {

    private THashMap<Integer, Room> user_visited = new THashMap<Integer, Room>();

    private volatile boolean status = false;

    private Habbo habboEjecute = null;
    private Room roomObjetive = null;
    private TreeMap<Integer, HabboItem> objetives = new TreeMap<Integer, HabboItem>();
    private HabboItem objetiveFinal = null;
    private final boolean pauseAtDestinationForPoliceTransport;
    private final boolean taxiBotRideMode;
    private final Habbo habbo;
    private final int roomObjectiveId;
    private volatile boolean started;

    public RecorridoTaxi(Habbo habbo, int roomId) {
        this(habbo, roomId, false, false, false);
    }

    public RecorridoTaxi(Habbo habbo, int roomId, boolean pauseAtDestinationForPoliceTransport) {
        this(habbo, roomId, pauseAtDestinationForPoliceTransport, false, false);
    }

    RecorridoTaxi(
            Habbo habbo,
            int roomId,
            boolean pauseAtDestinationForPoliceTransport,
            boolean taxiBotRideMode,
            boolean deferStart) {
        this.habbo = habbo;
        this.roomObjectiveId = roomId;
        this.pauseAtDestinationForPoliceTransport = pauseAtDestinationForPoliceTransport;
        this.taxiBotRideMode = taxiBotRideMode;
        this.setStatus(true);
        if (!deferStart) {
            this.start();
        }
    }

    public synchronized boolean start() {
        if (!this.status) {
            return false;
        }
        if (!this.started) {
            this.goTo(this.habbo, this.roomObjectiveId);
        }
        return this.started;
    }

    public void setStatus(boolean value) {
        this.status = value;
    }

    public boolean getStatus() {
        return this.status;
    }

    public boolean isStarted() {
        return this.started;
    }

    public boolean isTaxiBotRideMode() {
        return this.taxiBotRideMode;
    }

    public Room getRoomObjetive() {
        return roomObjetive;
    }

    public int getRoomMapa(String name) {
        return RoleplayWebsocketsRoomsInfo.getRoomsRoleplayManager()
                .getRoom(name)
                .getRoomId();
    }

    public String getControllerUserEnterRoom(Room roomObjetiveView, Room room) {

        String flechaObjetivo = "";

        if (room.getId() == this.getRoomMapa("calles_peligrosas_1")
                || room.getId() == this.getRoomMapa("calles_peligrosas_2")
                || room.getId() == this.getRoomMapa("supermercado")) {

            if (roomObjetiveView.getId() == this.getRoomMapa("calles_peligrosas_3")
                    || roomObjetiveView.getId() == this.getRoomMapa("hospital")
                    || roomObjetiveView.getId() == this.getRoomMapa("basurero")
                    || roomObjetiveView.getId() == this.getRoomMapa("centro_penitenciario")
                    || roomObjetiveView.getId() == this.getRoomMapa("calle_del_lago")
                    || roomObjetiveView.getId() == this.getRoomMapa("calle_del_puente")
                    || roomObjetiveView.getName().contains("[CASA]")
                    || roomObjetiveView.getId() == this.getRoomMapa("cafeteria")
                    || roomObjetiveView.getId() == this.getRoomMapa("calle_de_los_recursos")) {
                flechaObjetivo = "room_switcher_front_right";
            } else {
                flechaObjetivo = "room_switcher_back_left";
            }
        }

        if (room.getId() == this.getRoomMapa("calles_peligrosas_3")) {

            if (roomObjetiveView.getId() == this.getRoomMapa("hospital")
                    || roomObjetiveView.getId() == this.getRoomMapa("centro_penitenciario")
                    || roomObjetiveView.getId() == this.getRoomMapa("calle_del_lago")
                    || roomObjetiveView.getId() == this.getRoomMapa("calle_del_puente")
                    || roomObjetiveView.getId() == this.getRoomMapa("calle_de_los_recursos")
                    || roomObjetiveView.getName().contains("[CASA]")) {
                flechaObjetivo = "room_switcher_back_right";
            }

            if (roomObjetiveView.getId() == this.getRoomMapa("supermercado")
                    || roomObjetiveView.getId() == this.getRoomMapa("calles_peligrosas_2")
                    || roomObjetiveView.getId() == this.getRoomMapa("calles_peligrosas_1")) {
                flechaObjetivo = "room_switcher_back_left";
            }

            if (roomObjetiveView.getId() == this.getRoomMapa("basurero")) {
                flechaObjetivo = "room_switcher_front_left";
            }

            if (roomObjetiveView.getId() == this.getRoomMapa("cafeteria")) {
                flechaObjetivo = "room_switcher_front_right";
            }
        }

        if (room.getId() == this.getRoomMapa("basurero")) {
            flechaObjetivo = "room_switcher_back_right";
        }

        if (room.getId() == this.getRoomMapa("hospital")) {

            if (roomObjetiveView.getId() == this.getRoomMapa("centro_penitenciario")
                    || roomObjetiveView.getId() == this.getRoomMapa("calle_del_lago")
                    || roomObjetiveView.getId() == this.getRoomMapa("calle_del_puente")
                    || roomObjetiveView.getName().contains("[CASA]")
                    || roomObjetiveView.getId() == this.getRoomMapa("calle_de_los_recursos")) {
                flechaObjetivo = "room_switcher_back_right";
            }

            if (roomObjetiveView.getId() == this.getRoomMapa("supermercado")
                    || roomObjetiveView.getId() == this.getRoomMapa("calles_peligrosas_2")
                    || roomObjetiveView.getId() == this.getRoomMapa("calles_peligrosas_1")
                    || roomObjetiveView.getId() == this.getRoomMapa("calles_peligrosas_3")
                    || roomObjetiveView.getId() == this.getRoomMapa("cafeteria")
                    || roomObjetiveView.getId() == this.getRoomMapa("basurero")) {
                flechaObjetivo = "room_switcher_front_left";
            }
        }

        if (room.getId() == this.getRoomMapa("centro_penitenciario")) {

            if (roomObjetiveView.getId() == this.getRoomMapa("calle_del_puente")
                    || roomObjetiveView.getName().contains("[CASA]")
                    || roomObjetiveView.getId() == this.getRoomMapa("calle_de_los_recursos")) {
                flechaObjetivo = "room_switcher_back_right";
            }
            if (roomObjetiveView.getId() == this.getRoomMapa("supermercado")
                    || roomObjetiveView.getId() == this.getRoomMapa("calles_peligrosas_2")
                    || roomObjetiveView.getId() == this.getRoomMapa("calles_peligrosas_1")
                    || roomObjetiveView.getId() == this.getRoomMapa("calles_peligrosas_3")
                    || roomObjetiveView.getId() == this.getRoomMapa("calle_del_lago")
                    || roomObjetiveView.getId() == this.getRoomMapa("hospital")
                    || roomObjetiveView.getId() == this.getRoomMapa("basurero")
                    || roomObjetiveView.getId() == this.getRoomMapa("cafeteria")) {
                flechaObjetivo = "room_switcher_front_left";
            }
        }

        if (room.getId() == this.getRoomMapa("calle_del_lago")) {
            if (roomObjetiveView.getId() == this.getRoomMapa("centro_penitenciario")
                    || roomObjetiveView.getId() == this.getRoomMapa("calle_del_puente")
                    || roomObjetiveView.getName().contains("[CASA]")) {
                flechaObjetivo = "room_switcher_back_right";
            }
            if (roomObjetiveView.getId() == this.getRoomMapa("supermercado")
                    || roomObjetiveView.getId() == this.getRoomMapa("calles_peligrosas_2")
                    || roomObjetiveView.getId() == this.getRoomMapa("calles_peligrosas_1")
                    || roomObjetiveView.getId() == this.getRoomMapa("calles_peligrosas_3")
                    || roomObjetiveView.getId() == this.getRoomMapa("calle_del_lago")
                    || roomObjetiveView.getId() == this.getRoomMapa("hospital")
                    || roomObjetiveView.getId() == this.getRoomMapa("basurero")
                    || roomObjetiveView.getId() == this.getRoomMapa("cafeteria")) {
                flechaObjetivo = "room_switcher_front_left";
            }
        }

        if (room.getId() == this.getRoomMapa("cafeteria")) {

            flechaObjetivo = "room_switcher_back_left";
        }

        if (room.getId() == this.getRoomMapa("calle_del_puente")) {

            if (roomObjetiveView.getId() == this.getRoomMapa("calle_de_los_recursos")) {
                flechaObjetivo = "room_switcher_front_right";
            } else if (roomObjetiveView.getName().contains("[CASA]")) {
                flechaObjetivo = "room_switcher_home";
            } else {
                flechaObjetivo = "room_switcher_front_left";
            }
        }

        if (room.getId() == this.getRoomMapa("calle_de_los_recursos")) {
            flechaObjetivo = "room_switcher_back_left";
        }

        if (room.getName().contains("[CASA]")) {
            flechaObjetivo = "room_switcher_home";
        }

        return flechaObjetivo;
    }

    public void changeFlechasObjetiveGoto(Room room, String flechaObjetivo) {

        int cantidadFlechas = 0;
        for (HabboItem itemHabbo : room.getFloorItems()) {
            if (!itemHabbo.getBaseItem().getName().equalsIgnoreCase(flechaObjetivo)) {
                continue;
            }

            if (cantidadFlechas == 0) {
                this.objetives.clear();
            }
            cantidadFlechas++;
            this.objetives.put(itemHabbo.getId(), itemHabbo);
        }

        int increment = 0;
        for (HabboItem habboItem : objetives.values()) {
            objetiveFinal = habboItem;
            increment++;
            if (increment >= 1) {
                continue;
            }
        }
    }

    public void changeFlechasObjetive(Room room, String flechaObjetivo, HabboItem itemPisado) {
        List<HabboItem> flechasDeLaSala = new ArrayList<>();
        String nombreFlechaPisada = itemPisado.getBaseItem().getName();

        // 1. Buscamos todas las flechas del mismo tipo que la que pisó el usuario
        // (Entrada)
        for (HabboItem item : room.getFloorItems()) {
            if (item.getBaseItem().getName().equalsIgnoreCase(nombreFlechaPisada)) {
                flechasDeLaSala.add(item);
            }
        }

        // 2. Buscamos todas las flechas del tipo objetivo (Salida)
        List<HabboItem> flechasObjetivoLista = new ArrayList<>();
        for (HabboItem item : room.getFloorItems()) {
            if (item.getBaseItem().getName().equalsIgnoreCase(flechaObjetivo)) {
                flechasObjetivoLista.add(item);
            }
        }

        // 3. Ordenamos ambas listas igual que en el RoomTylesManager para que los
        // índices coincidan
        // Usamos el Eje Y si es una de las excepciones de diagonal, si no, Eje X.
        boolean useYAxis = (nombreFlechaPisada.contains("back_left") && flechaObjetivo.contains("front_right"))
                || (nombreFlechaPisada.contains("front_right") && flechaObjetivo.contains("back_left"));

        if (useYAxis) {
            flechasDeLaSala.sort((i1, i2) -> Short.compare(i1.getY(), i2.getY()));
            flechasObjetivoLista.sort((i1, i2) -> Short.compare(i1.getY(), i2.getY()));
        } else {
            flechasDeLaSala.sort((i1, i2) -> Short.compare(i1.getX(), i2.getX()));
            flechasObjetivoLista.sort((i1, i2) -> Short.compare(i1.getX(), i2.getX()));
        }

        // 4. Encontramos el índice de la flecha que el usuario pisó
        int indicePisado = flechasDeLaSala.indexOf(itemPisado);

        // 5. Asignamos la flecha objetivo que esté en esa misma posición
        if (indicePisado != -1 && indicePisado < flechasObjetivoLista.size()) {
            this.objetiveFinal = flechasObjetivoLista.get(indicePisado);

            // Actualizamos el mapa de objetivos por si el ciclo lo requiere
            this.objetives.clear();
            this.objetives.put(this.objetiveFinal.getId(), this.objetiveFinal);
        } else {
            // Fallback: si algo falla, elegir la primera disponible
            if (!flechasObjetivoLista.isEmpty()) {
                this.objetiveFinal = flechasObjetivoLista.get(0);
            }
        }
    }

    public void goTo(Habbo habbo, int room_id) {

        try {
            if (user_visited.get(habbo.getHabboInfo().getId()) != null) {
                user_visited.remove(habbo.getHabboInfo().getId());
            }

            Room room = habbo.getHabboInfo().getCurrentRoom();
            Room roomLoaded = Emulator.getGameEnvironment().getRoomManager().loadRoom(room_id);
            roomLoaded.loadData();
            roomLoaded.run();

            if (habbo != null && room != null) {

                HabboRp habboRp =
                        RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
                HabboClient habboClient = habboRp.getHabboClient();

                habboRp.setProgressPendiente(false);

                if (habboClient != null && habboRp != null) {

                    if (habboRp.getProgressPendiente() == true) {
                        return;
                    }

                    Room roomObjetiveView = roomLoaded;
                    String flechaObjetivo = getControllerUserEnterRoom(roomObjetiveView, room);

                    // EJECUTA LA LOGICA SI O SI
                    this.changeFlechasObjetiveGoto(room, flechaObjetivo);

                    this.roomObjetive = roomLoaded;
                    this.habboEjecute = habbo;
                    this.started = true;

                    this.execute(habbo);
                }
            }
        } catch (Exception e) {
            RoleplayWebsocketsRoomsInfo.utilities().LOGGER(e + "");
        }
    }

    public void execute(Habbo habbo) {
        Room room = habbo.getHabboInfo().getCurrentRoom();
        this.setStatus(true);
        this.cycle(room);
    }

    @Override
    public void cycle(Room room) {

        Runnable runnable = new Runnable() {
            @Override
            public void run() {

                // Esto se ejecuta en segundo plano una única vez
                while (true) {
                    try {
                        // En él, hacemos que el hilo duerma
                        Thread.sleep(taxiBotRideMode ? 150L : 1000L);

                        if (status == true) {

                            Habbo habbo = habboEjecute;
                            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager()
                                    .getHabboRp(habbo);

                            Room roomActual = habbo.getHabboInfo().getCurrentRoom();

                            if (roomActual == null) {
                                setStatus(false);
                                return;
                            }

                            if (roomObjetive == null) {
                                setStatus(false);
                                return;
                            }
                            habboRp.allowMove(true);

                            // RoleplayWebsocketsRoomsInfo.utilities().LOGGER(roomActual.getName() + "");
                            // RoleplayWebsocketsRoomsInfo.utilities().LOGGER(roomObjetive.getName() + "");

                            if (roomActual.getId() == roomObjetive.getId()) {
                                if (pauseAtDestinationForPoliceTransport) {
                                    setStatus(false);
                                    return;
                                }

                                if (taxiBotRideMode) {
                                    setStatus(false);
                                    RoleplayWebsocketsRoomsInfo.getControllerRecorridosTaxi()
                                            .finishTaxiBotRide(habbo);
                                    return;
                                }

                                Emulator.getThreading()
                                        .run(
                                                () -> {
                                                    RoleplayWebsocketsRoomsInfo.getControllerRecorridosTaxi()
                                                            .completeRecorridoTaxi(habbo);
                                                },
                                                1000);

                                return;
                            }

                            if (objetiveFinal == null) {
                                setStatus(false);
                                return;
                            }

                            if (taxiBotRideMode) {
                                RoleplayWebsocketsRoomsInfo.getControllerRecorridosTaxi()
                                        .guideTaxiBot(habbo, roomActual, objetiveFinal);
                                continue;
                            }

                            // Por defecto 634 // Personalizado 750
                            int enable_taxi =
                                    RoleplayWebsocketsRoomsInfo.getConfig().getInt("enable_taxi_id");
                            habboRp.setEnable(enable_taxi);
                            habbo.getHabboInfo().getCurrentRoom().giveEffect(habbo, Integer.valueOf(enable_taxi), -1);

                            // RoleplayWebsocketsRoomsInfo.utilities().LOGGER("" + objetiveFinal);

                            short x = (short) (objetiveFinal.getX() + 0);
                            short y = (short) (objetiveFinal.getY() + 0);
                            RoomTile tile = roomActual.getLayout().getTile(x, y);

                            if (tile.state == RoomTileState.INVALID || tile.state == RoomTileState.BLOCKED) {
                                RoomTile alternativeTile = null;
                                List<RoomTile> optionalTiles = room.getLayout().getTilesAround(tile);

                                Collections.reverse(optionalTiles);
                                for (RoomTile optionalTile : optionalTiles) {
                                    if (optionalTile.state != RoomTileState.INVALID
                                            && optionalTile.state != RoomTileState.BLOCKED) {
                                        alternativeTile = optionalTile;
                                        break;
                                    }
                                }
                                if (alternativeTile != null) {
                                    tile = alternativeTile;
                                }
                            }

                            if (tile != null && tile.isWalkable()) {

                                habbo.getRoomUnit().setGoalLocation(tile);
                            }

                        } else {
                            return;
                        }

                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                }
            }
        };

        Thread hilo = new Thread(runnable);
        hilo.start();
    }
}
