package com.srth.plugin.habbo.Delitos;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.rooms.ForwardToRoomComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserDataComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;
import com.eu.habbo.plugin.events.users.UserDisconnectEvent;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Rooms.Mapa.Taxi.ControllerRecorridosTaxi;
import com.srth.plugin.Rooms.Mapa.Taxi.RecorridoTaxi;
import com.srth.plugin.habbo.HabboRp;

public final class PoliceTransportManager {

    private static final Map<Integer, Transport> BY_POLICE = new HashMap<>();
    private static final Map<Integer, Transport> BY_PASSENGER = new HashMap<>();
    private static final Set<Integer> SKIP_DEFAULT_PRISON_PLACEMENT = new HashSet<>();

    private PoliceTransportManager() {
    }

    public static synchronized boolean isTransporting(Habbo habbo) {
        if (habbo == null) {
            return false;
        }

        int userId = habbo.getHabboInfo().getId();
        return BY_POLICE.containsKey(userId) || BY_PASSENGER.containsKey(userId);
    }

    public static synchronized boolean shouldSkipDefaultPrisonPlacement(Habbo habbo) {
        if (habbo == null) {
            return false;
        }

        int userId = habbo.getHabboInfo().getId();
        boolean skipThisEntry = SKIP_DEFAULT_PRISON_PLACEMENT.remove(userId);
        return skipThisEntry || BY_PASSENGER.containsKey(userId);
    }

    public static synchronized boolean canOffer(Habbo police, Habbo passenger) {
        if (police == null || passenger == null || police == passenger
                || isTransporting(police) || isTransporting(passenger)) {
            return false;
        }

        Room room = police.getHabboInfo().getCurrentRoom();
        if (room == null || room != passenger.getHabboInfo().getCurrentRoom()
                || room.getCategory() != RoleplayWebsocketsRoomsInfo.getConfig()
                        .getInt("global_room.category.roleplay.id")) {
            return false;
        }

        if (room.getId() == RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.job_police.id")) {
            return false;
        }

        RoomTile policeTile = police.getRoomUnit().getCurrentLocation();
        RoomTile passengerTile = passenger.getRoomUnit().getCurrentLocation();
        if (policeTile == null || passengerTile == null || policeTile.distance(passengerTile) > 10) {
            return false;
        }

        ControllerRecorridosTaxi taxiController = RoleplayWebsocketsRoomsInfo.getControllerRecorridosTaxi();
        return taxiController != null && taxiController.getRecorridoTaxi(police) == null
                && taxiController.getRecorridoTaxi(passenger) == null;
    }

    public static synchronized boolean start(Habbo police, Habbo passenger) throws SQLException {
        if (!canOffer(police, passenger)) {
            whisper(police, "No puedes iniciar el traslado: acércate al usuario y verifica que ninguno esté en otro traslado.");
            return false;
        }

        HabboRp policeRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(police);
        if (policeRp == null || policeRp.getJobActive() == null
                || !policeRp.getJobActive().getName().equalsIgnoreCase("police")) {
            whisper(police, "Debes tener activo el trabajo de policía para llevar a un usuario.");
            return false;
        }

        if (policeRp.getHabboTutorial() == null || !policeRp.getHabboTutorial().getCompleted()) {
            whisper(police, "Debes completar el tutorial antes de realizar un traslado policial.");
            return false;
        }

        HabboRp passengerRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(passenger);
        if (passengerRp == null) {
            whisper(police, "No se pudo cargar la información del usuario.");
            return false;
        }

        int destinationRoomId = RoleplayWebsocketsRoomsInfo.getJobsManager().getJob("police").getRoomId();
        if (destinationRoomId < 1) {
            whisper(police, "No se configuró la sala de la estación policial.");
            return false;
        }

        if (police.getHabboInfo().getCurrentRoom().getId() == destinationRoomId) {
            whisper(police, "El usuario ya está en la estación policial.");
            return false;
        }

        Room destinationRoom = Emulator.getGameEnvironment().getRoomManager().loadRoom(destinationRoomId);
        if (destinationRoom == null) {
            whisper(police, "No se encontró la sala de destino de la estación policial.");
            return false;
        }

        int taxiEnable = RoleplayWebsocketsRoomsInfo.getConfig().getInt("enable_taxi_id");
        Transport transport = new Transport(police, passenger, policeRp, passengerRp, passenger.getHabboInfo().getLook(),
                passenger.getRoomUnit().canWalk(), passenger.getRoomUnit().getEffectId(),
                passengerRp.getEnable(), taxiEnable, police.getRoomUnit().isFastWalk(), destinationRoomId);
        BY_POLICE.put(police.getHabboInfo().getId(), transport);
        BY_PASSENGER.put(passenger.getHabboInfo().getId(), transport);

        try {
            police.getRoomUnit().setFastWalk(true);
            hideAndFollow(transport);
            Room passengerRoom = passenger.getHabboInfo().getCurrentRoom();
            passengerRp.setEnable(taxiEnable);
            passengerRoom.giveEffect(passenger, taxiEnable, -1);
            RecorridoTaxi route = RoleplayWebsocketsRoomsInfo.getControllerRecorridosTaxi()
                    .setPoliceTransportTaxi(police, destinationRoomId);
            if (route == null) {
                release(transport, true);
                whisper(police, "No se pudo iniciar el recorrido hacia la estación policial.");
                return false;
            }

            whisper(police, "Traslado iniciado. Lleva el vehículo hasta la estación policial.");
            whisper(passenger, "Un policía te está trasladando a la estación policial.");
            return true;
        } catch (RuntimeException exception) {
            release(transport, true);
            throw exception;
        }
    }

    public static synchronized void onPoliceStep(Habbo habbo) {
        if (habbo == null) {
            return;
        }

        Transport asPassenger = BY_PASSENGER.get(habbo.getHabboInfo().getId());
        if (asPassenger != null) {
            hideAndFollow(asPassenger);
            return;
        }

        Habbo police = habbo;
        Transport transport = BY_POLICE.get(police.getHabboInfo().getId());
        if (transport == null) {
            return;
        }
        police.getRoomUnit().setFastWalk(true);

        RecorridoTaxi route = RoleplayWebsocketsRoomsInfo.getControllerRecorridosTaxi()
                .getRecorridoTaxi(police);
        Room room = police.getHabboInfo().getCurrentRoom();
        if (transport.arrivedAtStation || room != null && room.getId() == transport.destinationRoomId) {
            transport.arrivedAtStation = true;
            preparePrisonEntry(transport);
            return;
        }

        if ((route == null || !route.getStatus()) && (room == null || room.getId() != transport.destinationRoomId)) {
            release(transport, true);
            whisper(transport.police, "El traslado policial se interrumpió.");
            whisper(transport.passenger, "El traslado policial se interrumpió.");
            return;
        }

        hideAndFollow(transport);
    }

    public static synchronized void onUserEnterRoom(Habbo habbo, Room room) {
        if (habbo == null || room == null) {
            return;
        }

        Transport asPolice = BY_POLICE.get(habbo.getHabboInfo().getId());
        if (asPolice != null) {
            habbo.getRoomUnit().setFastWalk(true);
            Emulator.getThreading().run(() -> {
                synchronized (PoliceTransportManager.class) {
                    if (BY_POLICE.get(habbo.getHabboInfo().getId()) == asPolice
                            && habbo.getHabboInfo().getCurrentRoom() == room) {
                        habbo.getRoomUnit().setFastWalk(true);
                    }
                }
            }, 500);
            asPolice.arrivedAtStation = room.getId() == asPolice.destinationRoomId;
            forwardPassenger(asPolice, room);
            if (asPolice.arrivedAtStation && asPolice.passenger.getHabboInfo().getCurrentRoom() == room) {
                Emulator.getThreading().run(() -> {
                    synchronized (PoliceTransportManager.class) {
                        preparePrisonEntry(asPolice);
                    }
                }, 100);
            }
            return;
        }

        Transport asPassenger = BY_PASSENGER.get(habbo.getHabboInfo().getId());
        if (asPassenger == null) {
            return;
        }

        Room policeRoom = asPassenger.police.getHabboInfo().getCurrentRoom();
        if (policeRoom == null) {
            return;
        }

        if (policeRoom != room) {
            forwardPassenger(asPassenger, policeRoom);
            return;
        }

        if (asPassenger.arrivedAtStation && room.getId() == asPassenger.destinationRoomId) {
            SKIP_DEFAULT_PRISON_PLACEMENT.add(habbo.getHabboInfo().getId());
            Emulator.getThreading().run(() -> {
                synchronized (PoliceTransportManager.class) {
                    preparePrisonEntry(asPassenger);
                }
            }, 100);
        } else {
            hideAndFollow(asPassenger);
        }
    }

    public static synchronized void cancelForPolice(Habbo police) {
        if (police == null) {
            return;
        }

        Transport transport = BY_POLICE.get(police.getHabboInfo().getId());
        if (transport != null) {
            release(transport, false);
            whisper(transport.passenger, "El traslado policial fue cancelado.");
        }
    }

    public static synchronized void handleLogout(UserDisconnectEvent event) {
        if (event == null || event.habbo == null) {
            return;
        }

        Habbo habbo = event.habbo;
        SKIP_DEFAULT_PRISON_PLACEMENT.remove(habbo.getHabboInfo().getId());
        Transport transport = BY_POLICE.get(habbo.getHabboInfo().getId());
        if (transport == null) {
            transport = BY_PASSENGER.get(habbo.getHabboInfo().getId());
        }
        if (transport == null) {
            return;
        }

        boolean policeLoggedOut = transport.police == habbo;
        release(transport, true);
        if (!policeLoggedOut) {
            whisper(transport.police, "El usuario salió del hotel; el traslado fue cancelado.");
        }
    }

    private static void forwardPassenger(Transport transport, Room room) {
        Habbo passenger = transport.passenger;
        if (room == null || passenger.getHabboInfo().getCurrentRoom() == room || passenger.getClient() == null) {
            return;
        }

        passenger.getClient().sendResponse(new ForwardToRoomComposer(room.getId()));
    }

    private static void hideAndFollow(Transport transport) {
        Habbo police = transport.police;
        Habbo passenger = transport.passenger;
        Room room = police.getHabboInfo().getCurrentRoom();
        RoomTile policeTile = police.getRoomUnit().getCurrentLocation();
        if (room == null || room != passenger.getHabboInfo().getCurrentRoom() || policeTile == null) {
            return;
        }

        passenger.getRoomUnit().setCanWalk(false);
        if (passenger.getRoomUnit().getEffectId() != transport.taxiEnable) {
            transport.passengerRp.setEnable(transport.taxiEnable);
            room.giveEffect(passenger, transport.taxiEnable, -1);
        }
        boolean lookChanged = !passenger.getHabboInfo().getLook().isEmpty();
        if (lookChanged) {
            passenger.getHabboInfo().setLook("");
        }
        passenger.getRoomUnit().setLocation(policeTile);
        passenger.getRoomUnit().setGoalLocation(policeTile);
        passenger.getRoomUnit().setRotation(police.getRoomUnit().getBodyRotation());
        if (lookChanged) {
            room.sendComposer(new RoomUserDataComposer(passenger).compose());
        }
        room.sendComposer(new RoomUserStatusComposer(passenger.getRoomUnit()).compose());
    }

    private static void preparePrisonEntry(Transport transport) {
        Room room = transport.police.getHabboInfo().getCurrentRoom();
        if (room == null || room.getId() != transport.destinationRoomId
                || room != transport.passenger.getHabboInfo().getCurrentRoom()) {
            return;
        }

        if (transport.gate == null) {
            HabboItem gate = null;
            for (HabboItem item : room.getFloorItems()) {
                if (item.getBaseItem().getName().equalsIgnoreCase("prison_gate")) {
                    gate = item;
                    break;
                }
            }

            if (gate == null) {
                sendPrisonerToRocks(transport, room,
                        "No se encontró prison_gate; se ingresará al prisionero en las rocas.");
                return;
            }

            RoomTile gateBase = room.getLayout().getTile(gate.getX(), gate.getY());
            if (gateBase == null) {
                sendPrisonerToRocks(transport, room,
                        "No se pudo determinar prison_gate; se ingresará al prisionero en las rocas.");
                return;
            }

            int facing = Math.floorMod(gate.getRotation(), 8);
            RoomTile approach = findGateApproachTile(room, gateBase, facing);
            if (approach == null) {
                sendPrisonerToRocks(transport, room,
                        "No hay una baldosa transitable delante de prison_gate; se ingresará al prisionero en las rocas.");
                return;
            }

            transport.gate = gate;
            transport.gateApproach = approach;
        }

        RoomTile policeTile = transport.police.getRoomUnit().getCurrentLocation();
        if (policeTile == null) {
            return;
        }

        if (policeTile.distance(transport.gateApproach) > 0) {
            transport.police.getRoomUnit().setGoalLocation(transport.gateApproach);
            return;
        }

        sendPrisonerToRocks(transport, room, "El policía llegó a la puerta de la prisión.");
    }

    private static void sendPrisonerToRocks(Transport transport, Room room, String message) {
        transport.passengerRp.getDelitosHabbo().setEncarceled(true);
        boolean delivered = new PolicesManager()
                .sendTransportedPrisonerToRandomRock(room, transport.passenger);
        finishTaxiRoute(transport);
        release(transport, false, !delivered);
        if (delivered) {
            whisper(transport.passenger, "Llegaste a prisión.");
            whisper(transport.police, message + " El usuario fue enviado a una roca policial.");
        } else {
            transport.passengerRp.getDelitosHabbo().changeLookPrisionero(transport.passenger);
            whisper(transport.police, "No hay rocas policiales transitables en esta sala.");
        }
    }

    private static RoomTile findGateApproachTile(Room room, RoomTile gateBase, int facing) {
        RoomTile tileInFront = room.getLayout().getTileInFront(gateBase, facing);
        if (isPassableTile(tileInFront)) {
            return tileInFront;
        }

        return findAdjacentTileOnSide(room, gateBase, facing);
    }

    private static RoomTile findAdjacentTileOnSide(Room room, RoomTile gateBase, int facing) {
        int[] direction = getDirection(facing);
        RoomTile bestTile = null;
        int bestDistance = Integer.MAX_VALUE;

        for (RoomTile tile : room.getLayout().getTilesAround(gateBase)) {
            int offsetX = tile.x - gateBase.x;
            int offsetY = tile.y - gateBase.y;
            if (!isPassableTile(tile) || offsetX * direction[0] + offsetY * direction[1] <= 0) {
                continue;
            }

            int distance = (offsetX - direction[0]) * (offsetX - direction[0])
                    + (offsetY - direction[1]) * (offsetY - direction[1]);
            if (distance < bestDistance) {
                bestTile = tile;
                bestDistance = distance;
            }
        }

        return bestTile;
    }

    private static boolean isPassableTile(RoomTile tile) {
        return tile != null && tile.state != RoomTileState.INVALID
                && tile.state != RoomTileState.BLOCKED && tile.state != RoomTileState.LAY;
    }

    private static int[] getDirection(int rotation) {
        switch (Math.floorMod(rotation, 8)) {
            case 0:
                return new int[] { 0, -1 };
            case 1:
                return new int[] { 1, -1 };
            case 2:
                return new int[] { 1, 0 };
            case 3:
                return new int[] { 1, 1 };
            case 4:
                return new int[] { 0, 1 };
            case 5:
                return new int[] { -1, 1 };
            case 6:
                return new int[] { -1, 0 };
            default:
                return new int[] { -1, -1 };
        }
    }

    private static void finishTaxiRoute(Transport transport) {
        ControllerRecorridosTaxi controller = RoleplayWebsocketsRoomsInfo.getControllerRecorridosTaxi();
        if (controller != null && controller.getRecorridoTaxi(transport.police) != null) {
            controller.removeRecorridoTaxi(transport.police);
        }

        Room room = transport.police.getHabboInfo().getCurrentRoom();
        RoomTile currentTile = transport.police.getRoomUnit().getCurrentLocation();
        if (currentTile != null) {
            transport.police.getRoomUnit().setGoalLocation(currentTile);
        }
        if (room != null) {
            room.giveEffect(transport.police, 0, -1);
        }
        transport.policeRp.setEnable(0);
        transport.policeRp.registerCache("existConfirmacionExitRecorrido", "0");
    }

    private static void release(Transport transport, boolean cancelTaxiRoute) {
        release(transport, cancelTaxiRoute, true);
    }

    private static void release(Transport transport, boolean cancelTaxiRoute, boolean separatePassenger) {
        BY_POLICE.remove(transport.police.getHabboInfo().getId(), transport);
        BY_PASSENGER.remove(transport.passenger.getHabboInfo().getId(), transport);

        if (cancelTaxiRoute) {
            ControllerRecorridosTaxi controller = RoleplayWebsocketsRoomsInfo.getControllerRecorridosTaxi();
            if (controller != null && controller.getRecorridoTaxi(transport.police) != null) {
                controller.eventFinaliceRecorrido(transport.police);
            }
        }

        Habbo passenger = transport.passenger;
        passenger.getRoomUnit().setCanWalk(transport.passengerCouldWalk);
        transport.police.getRoomUnit().setFastWalk(transport.policeFastWalk);
        transport.passengerRp.setEnable(transport.passengerEnable);
        passenger.getHabboInfo().setLook(transport.passengerLook);
        Room room = passenger.getHabboInfo().getCurrentRoom();
        if (room != null) {
            room.giveEffect(passenger, transport.passengerEffect, -1);
        }
        if (room != null) {
            if (separatePassenger && room == transport.police.getHabboInfo().getCurrentRoom()) {
                RoomTile policeTile = transport.police.getRoomUnit().getCurrentLocation();
                if (policeTile != null) {
                    for (RoomTile nearbyTile : room.getLayout().getTilesAround(policeTile)) {
                        if (nearbyTile != null && nearbyTile.isWalkable()) {
                            passenger.getRoomUnit().setLocation(nearbyTile);
                            break;
                        }
                    }
                }
            }
            room.sendComposer(new RoomUserDataComposer(passenger).compose());
            room.sendComposer(new RoomUserStatusComposer(passenger.getRoomUnit()).compose());
        }
    }

    private static void whisper(Habbo habbo, String message) {
        if (habbo != null && habbo.getClient() != null) {
            habbo.whisper(message, RoomChatMessageBubbles.NORMAL);
        }
    }

    private static final class Transport {
        private final Habbo police;
        private final Habbo passenger;
        private final HabboRp policeRp;
        private final HabboRp passengerRp;
        private final String passengerLook;
        private final boolean passengerCouldWalk;
        private final int passengerEffect;
        private final int passengerEnable;
        private final int taxiEnable;
        private final boolean policeFastWalk;
        private final int destinationRoomId;
        private boolean arrivedAtStation;
        private HabboItem gate;
        private RoomTile gateApproach;

        private Transport(Habbo police, Habbo passenger, HabboRp policeRp, HabboRp passengerRp, String passengerLook,
                boolean passengerCouldWalk, int passengerEffect, int passengerEnable, int taxiEnable,
                boolean policeFastWalk, int destinationRoomId) {
            this.police = police;
            this.passenger = passenger;
            this.policeRp = policeRp;
            this.passengerRp = passengerRp;
            this.passengerLook = passengerLook;
            this.passengerCouldWalk = passengerCouldWalk;
            this.passengerEffect = passengerEffect;
            this.passengerEnable = passengerEnable;
            this.taxiEnable = taxiEnable;
            this.policeFastWalk = policeFastWalk;
            this.destinationRoomId = destinationRoomId;
        }
    }
}
