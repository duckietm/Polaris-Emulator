package com.srth.plugin.Rooms.Mapa.Taxi;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.items.interactions.InteractionTeleport;
import com.eu.habbo.habbohotel.items.interactions.InteractionTeleportTile;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitType;
import com.eu.habbo.habbohotel.rooms.RoomUserRotation;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboGender;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUsersComposer;
import com.eu.habbo.util.pathfinding.Rotation;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.interactions.vidayhambre.Teleports.InteractionTeleports;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TaxiBotRideManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaxiBotRideManager.class);
    private static final long PICKUP_DELAY_MS = 3000L;
    private static final long DESTINATION_PAUSE_MS = 2000L;
    private static final long DESTINATION_POLL_INTERVAL_MS = 100L;
    private static final long DESTINATION_WALK_TIMEOUT_NS = 10_000_000_000L;
    private static final long BOT_DEPARTURE_TIMEOUT_NS = 15_000_000_000L;
    private static final long APPROACH_POLL_INTERVAL_MS = 250L;
    private static final long APPROACH_TIMEOUT_NS = 45_000_000_000L;
    private static final String TAXI_DRIVER_FIGURE = "hr-100-61.hd-180-1.ch-210-66.lg-270-82.sh-290-80.ha-1002-63";
    private static final AtomicInteger NEXT_BOT_ID = new AtomicInteger(1_500_000_000);
    private static final Map<Integer, Ride> RIDES = new HashMap<>();

    private TaxiBotRideManager() {}

    static boolean start(Habbo passenger, RecorridoTaxi route) {
        if (passenger == null || route == null || passenger.getHabboInfo() == null || passenger.getRoomUnit() == null) {
            return false;
        }

        Room room = passenger.getHabboInfo().getCurrentRoom();
        HabboRp passengerRp;
        try {
            passengerRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(passenger);
        } catch (SQLException exception) {
            LOGGER.error(
                    "Could not load taxi passenger state for user {}",
                    passenger.getHabboInfo().getId(),
                    exception);
            return false;
        }
        if (room == null || passengerRp == null) {
            return false;
        }

        Ride ride = new Ride(passenger, route, passengerRp, room);
        synchronized (TaxiBotRideManager.class) {
            if (RIDES.containsKey(ride.passengerId)) {
                return false;
            }
            RIDES.put(ride.passengerId, ride);
            passenger.getRoomUnit().setCanWalk(false);
        }

        try {
            Emulator.getThreading().run(() -> pickUp(ride), PICKUP_DELAY_MS);
        } catch (RuntimeException exception) {
            cancel(passenger);
            throw exception;
        }

        passenger.whisper(
                "El taxi llegará en 3 segundos. Haz clic para cancelar el recorrido.", RoomChatMessageBubbles.NORMAL);
        return true;
    }

    static void onUserEnterRoom(Habbo passenger, Room room) {
        if (passenger == null || room == null || passenger.getHabboInfo() == null) {
            return;
        }

        Ride ride;
        boolean failed = false;
        boolean rideStarted = false;
        synchronized (TaxiBotRideManager.class) {
            ride = RIDES.get(passenger.getHabboInfo().getId());
            if (ride == null
                    || ride.state == RideState.EXITING
                    || ride.state == RideState.DEPARTING
                    || passenger.getHabboInfo().getCurrentRoom() != room) {
                return;
            }

            if (ride.lastRoom != room) {
                ride.previousRoom = ride.lastRoom;
                ride.lastRoom = room;
            }

            if (ride.state == RideState.ACTIVE || ride.state == RideState.APPROACHING) {
                rideStarted = ride.route.isStarted();
                HabboItem entryArrow = findEntryArrow(ride, room);
                failed = !moveBotToRoom(ride, room, entryArrow);
            }
        }

        if (failed) {
            abort(ride, "El taxi no pudo continuar en esta sala.");
        } else if (ride != null) {
            if (rideStarted) {
                RoomUnit passengerUnit = passenger.getRoomUnit();
                if (passengerUnit != null) {
                    passengerUnit.setCanWalk(true);
                    passengerUnit.setFastWalk(true);
                    room.giveEffect(passenger, ride.taxiEffect, -1);
                    ride.passengerRp.setEnable(ride.taxiEffect);
                }
            }
            if (ride.state == RideState.APPROACHING) {
                scheduleApproachCheck(ride, System.nanoTime() + APPROACH_TIMEOUT_NS);
            }
        }
    }

    static boolean cancel(Habbo passenger) {
        if (passenger == null || passenger.getHabboInfo() == null) {
            return false;
        }

        Ride ride;
        synchronized (TaxiBotRideManager.class) {
            ride = RIDES.remove(passenger.getHabboInfo().getId());
            if (ride == null) {
                return false;
            }
            ride.state = RideState.CANCELLED;
            cleanup(ride);
        }
        return true;
    }

    public static void handleLogout(Habbo passenger) {
        if (cancel(passenger)) {
            RoleplayWebsocketsRoomsInfo.getControllerRecorridosTaxi().removeRecorridoTaxi(passenger);
        }
    }

    static void finishAtDestination(Habbo passenger) {
        if (passenger == null || passenger.getHabboInfo() == null) {
            return;
        }

        Ride ride;
        Room room = passenger.getHabboInfo().getCurrentRoom();
        RoomTile walkGoal = null;
        synchronized (TaxiBotRideManager.class) {
            ride = RIDES.get(passenger.getHabboInfo().getId());
            if (ride == null || !canFinishAtDestination(ride.state)) {
                return;
            }

            ride.state = RideState.EXITING;
            RoomUnit passengerUnit = passenger.getRoomUnit();
            if (room != null && passengerUnit != null) {
                ensureBotInDestinationRoom(ride, room, passengerUnit);
            }

            RoomUnit botUnit = ride.bot == null ? null : ride.bot.getRoomUnit();
            if (room != null && ride.bot != null && ride.bot.getRoom() == room && botUnit != null) {
                walkGoal = findArrivalWalkGoal(room, botUnit, ride.botRotation);
                if (walkGoal != null) {
                    ride.bot.stopFollowingHabbo();
                    botUnit.setCanWalk(true);
                    botUnit.setRotation(ride.botRotation);
                    botUnit.setGoalLocation(walkGoal);
                    room.sendComposer(new RoomUserStatusComposer(botUnit).compose());
                }
            }
        }

        if (walkGoal == null) {
            scheduleDestinationCompletion(ride);
            return;
        }

        long deadline = System.nanoTime() + DESTINATION_WALK_TIMEOUT_NS;
        RoomTile target = walkGoal;
        Emulator.getThreading().run(() -> pollDestinationWalk(ride, target, deadline), DESTINATION_POLL_INTERVAL_MS);
    }

    static void guideRide(Habbo passenger, Room room, HabboItem exitArrow) {
        if (passenger == null || room == null || exitArrow == null || passenger.getHabboInfo() == null) {
            return;
        }

        Ride ride;
        synchronized (TaxiBotRideManager.class) {
            ride = RIDES.get(passenger.getHabboInfo().getId());
            if (ride == null
                    || ride.state != RideState.ACTIVE
                    || passenger.getHabboInfo().getCurrentRoom() != room
                    || ride.bot == null
                    || passenger.getRoomUnit() == null) {
                return;
            }

            RoomTile target = room.getLayout().getTile(exitArrow.getX(), exitArrow.getY());
            if (target == null || !target.isWalkable()) {
                return;
            }

            RoomUnit passengerUnit = passenger.getRoomUnit();
            if (ride.botInTransit) {
                if (ride.lastRoom == room && !target.equals(passengerUnit.getGoal())) {
                    passengerUnit.setGoalLocation(target);
                }
                return;
            }

            if (ride.bot.getRoom() != room || ride.bot.getRoomUnit() == null) {
                return;
            }

            if (ride.targetArrowId != exitArrow.getId()) {
                ride.targetArrowId = exitArrow.getId();
                cacheTeleportDestination(ride, room, exitArrow);
            }

            RoomUnit botUnit = ride.bot.getRoomUnit();
            RoomTile botTile = botUnit.getCurrentLocation();
            if (botTile == null || passengerUnit.getCurrentLocation() == null) {
                return;
            }

            if (target.equals(botTile) && !botUnit.isWalking()) {
                ride.botRotation = botUnit.getBodyRotation();
                ride.botInTransit = true;
                removeBot(ride.bot);
                if (!target.equals(passengerUnit.getGoal())) {
                    passengerUnit.setGoalLocation(target);
                }
                return;
            }

            if (!botUnit.isWalking() && !target.equals(botUnit.getGoal())) {
                botUnit.setGoalLocation(target);
            }

            RoomTile followTile =
                    botUnit.isWalking() ? botUnit.getPreviousLocation() : findTileBehindBot(room, botUnit, target);
            if (followTile != null
                    && !followTile.equals(passengerUnit.getCurrentLocation())
                    && !followTile.equals(passengerUnit.getGoal())) {
                passengerUnit.setGoalLocation(followTile);
            }
        }
    }

    static RoomTile findArrivalWalkGoal(Room room, RoomUnit botUnit, RoomUserRotation arrivalRotation) {
        if (room == null
                || room.getLayout() == null
                || botUnit == null
                || botUnit.getCurrentLocation() == null
                || arrivalRotation == null) {
            return null;
        }

        RoomTile goal = botUnit.getCurrentLocation();
        for (int step = 0; step < 2; step++) {
            RoomTile next = room.getLayout().getTileInFront(goal, arrivalRotation.getValue());
            if (!isAvailableSpawnTile(room, next)) {
                break;
            }
            goal = next;
        }
        return goal.equals(botUnit.getCurrentLocation()) ? null : goal;
    }

    private static void pickUp(Ride ride) {
        RuntimeException failure = null;
        long approachDeadline = System.nanoTime() + APPROACH_TIMEOUT_NS;
        synchronized (TaxiBotRideManager.class) {
            if (RIDES.get(ride.passengerId) != ride || ride.state != RideState.WAITING) {
                return;
            }

            Room room = ride.passenger.getHabboInfo().getCurrentRoom();
            if (room == null || ride.passenger.getRoomUnit() == null) {
                failure = new IllegalStateException("Passenger left the hotel before taxi pickup.");
            } else {
                ride.lastRoom = room;
                try {
                    ride.bot = createBot(ride, room);
                    ride.approachTile = findApproachTile(room, ride.passenger.getRoomUnit());
                    if (ride.approachTile == null) {
                        throw new IllegalStateException("No walkable tile is available in front of the passenger.");
                    }
                    ride.state = RideState.APPROACHING;
                    ride.bot.getRoomUnit().setGoalLocation(ride.approachTile);
                } catch (RuntimeException exception) {
                    failure = exception;
                }
            }

            if (failure != null) {
                RIDES.remove(ride.passengerId, ride);
                ride.state = RideState.CANCELLED;
                cleanup(ride);
            }
        }

        if (failure != null) {
            abort(ride, "No se pudo iniciar el taxi. El recorrido fue cancelado.");
            LOGGER.error("Could not start taxi bot ride for user {}", ride.passengerId, failure);
        } else {
            scheduleApproachCheck(ride, approachDeadline);
        }
    }

    private static Bot createBot(Ride ride, Room room) {
        RoomTile spawnTile = findTeleportSpawnTile(room, ride.passenger.getRoomUnit());
        if (spawnTile == null) {
            throw new IllegalStateException("No usable teleport tile is available for the taxi bot.");
        }

        Bot bot = new Bot(
                NEXT_BOT_ID.getAndUpdate(id -> id >= 1_999_999_999 ? 1_500_000_000 : id + 1),
                "Taxista",
                "Conduciendo un taxi",
                TAXI_DRIVER_FIGURE,
                HabboGender.M,
                room.getOwnerId(),
                room.getOwnerName());
        bot.setCanWalk(false);
        bot.setEffect(ride.taxiEffect, Integer.MAX_VALUE);
        bot.needsUpdate(false);
        addBotToRoom(bot, room, spawnTile, ride.taxiEffect, RoomUserRotation.EAST);
        return bot;
    }

    private static boolean moveBotToRoom(Ride ride, Room room, HabboItem entryArrow) {
        RoomTile spawnTile = findArrowSpawnTile(room, entryArrow, ride.passenger.getRoomUnit());
        if (spawnTile == null) {
            return false;
        }

        removeBot(ride.bot);
        addBotToRoom(ride.bot, room, spawnTile, ride.taxiEffect, ride.botRotation);
        ride.destinationEntryArrowId = entryArrow == null ? 0 : entryArrow.getId();
        ride.approachTile = findApproachTile(room, ride.passenger.getRoomUnit());
        if (ride.approachTile == null) {
            return false;
        }
        ride.state = RideState.APPROACHING;
        ride.botInTransit = false;
        ride.targetArrowId = 0;
        ride.bot.getRoomUnit().setGoalLocation(ride.approachTile);
        return true;
    }

    static void addBotToRoom(Bot bot, Room room, RoomTile tile, int taxiEffect, RoomUserRotation rotation) {
        RoomUnit unit = new RoomUnit();
        unit.setRoomUnitType(RoomUnitType.BOT);
        unit.setRoom(room);
        unit.setLocation(tile);
        unit.setZ(tile.getStackHeight());
        unit.setPreviousLocationZ(tile.getStackHeight());
        unit.setRotation(rotation);
        unit.setCanWalk(true);
        unit.setFastWalk(true);
        unit.setInRoom(true);
        unit.setPathFinderRoom(room);
        bot.setRoom(room);
        bot.setRoomUnit(unit);
        room.addBot(bot);
        room.sendComposer(new RoomUsersComposer(bot).compose());
        room.sendComposer(new RoomUserStatusComposer(unit).compose());
        room.giveEffect(unit, taxiEffect, -1);
    }

    static RoomTile findTeleportSpawnTile(Room room, RoomUnit passengerUnit) {
        if (room == null
                || room.getLayout() == null
                || passengerUnit == null
                || passengerUnit.getCurrentLocation() == null) {
            return null;
        }

        RoomTile passengerTile = passengerUnit.getCurrentLocation();
        return room.getFloorItems().stream()
                .filter(TaxiBotRideManager::isTeleportTile)
                .sorted(Comparator.comparingDouble(item -> {
                    RoomTile tile = room.getLayout().getTile(item.getX(), item.getY());
                    return tile == null ? Double.MAX_VALUE : tile.distance(passengerTile);
                }))
                .map(item -> findArrowSpawnTile(room, item, passengerUnit))
                .filter(tile -> tile != null)
                .findFirst()
                .orElse(null);
    }

    static RoomTile findApproachTile(Room room, RoomUnit passengerUnit) {
        if (room == null
                || room.getLayout() == null
                || passengerUnit == null
                || passengerUnit.getCurrentLocation() == null) {
            return null;
        }

        RoomTile passengerTile = passengerUnit.getCurrentLocation();
        RoomTile facingTile = room.getLayout()
                .getTileInFront(passengerTile, passengerUnit.getBodyRotation().getValue());
        if (isAvailableSpawnTile(room, facingTile)) {
            return facingTile;
        }

        return room.getLayout().getTilesAround(passengerTile).stream()
                .filter(tile -> isAvailableSpawnTile(room, tile))
                .min(Comparator.comparingDouble(tile -> tile.distance(facingTile == null ? passengerTile : facingTile)))
                .orElse(null);
    }

    private static boolean isAvailableSpawnTile(Room room, RoomTile tile) {
        if (tile == null
                || !tile.isWalkable()
                || tile.state == RoomTileState.INVALID
                || tile.state == RoomTileState.BLOCKED) {
            return false;
        }

        return room.getRoomUnitsAt(tile).isEmpty();
    }

    private static RoomTile findArrowSpawnTile(Room room, HabboItem arrow, RoomUnit passengerUnit) {
        if (room == null || room.getLayout() == null || arrow == null || passengerUnit == null) {
            return null;
        }

        RoomTile arrowTile = room.getLayout().getTile(arrow.getX(), arrow.getY());
        if (arrowTile == null) {
            return null;
        }
        if (isAvailableSpawnTile(room, arrowTile)) {
            return arrowTile;
        }

        return room.getLayout().getTilesAround(arrowTile).stream()
                .filter(tile -> isAvailableSpawnTile(room, tile))
                .min(Comparator.comparingDouble(tile -> tile.distance(arrowTile)))
                .orElse(null);
    }

    private static boolean isTeleportTile(HabboItem item) {
        if (item == null || item.getBaseItem() == null || item.getBaseItem().getInteractionType() == null) {
            return false;
        }

        Class<? extends HabboItem> interactionClass =
                item.getBaseItem().getInteractionType().getType();
        return interactionClass != null
                && (InteractionTeleportTile.class.isAssignableFrom(interactionClass)
                        || InteractionTeleports.class.isAssignableFrom(interactionClass));
    }

    private static HabboItem findEntryArrow(Ride ride, Room room) {
        RoomTile userTile = ride.passenger.getRoomUnit() == null
                ? null
                : ride.passenger.getRoomUnit().getCurrentLocation();
        return findEntryArrow(room, ride.expectedNextRoomId, ride.expectedEntryItemId, userTile);
    }

    static HabboItem findEntryArrow(Room room, int expectedRoomId, int expectedItemId, RoomTile userTile) {
        if (room == null) {
            return null;
        }

        boolean expectedRoomMatches = expectedRoomId <= 0 || expectedRoomId == room.getId();
        if (expectedRoomMatches && expectedItemId > 0) {
            HabboItem target = room.getHabboItem(expectedItemId);
            if (isTeleportTile(target)) {
                return target;
            }
        }

        if (userTile == null || room.getLayout() == null) {
            return null;
        }
        return room.getFloorItems().stream()
                .filter(TaxiBotRideManager::isTeleportTile)
                .min(Comparator.comparingDouble(item -> {
                    RoomTile tile = room.getLayout().getTile(item.getX(), item.getY());
                    return tile == null ? Double.MAX_VALUE : tile.distance(userTile);
                }))
                .orElse(null);
    }

    static HabboItem findDepartureArrow(
            Room room, int entryArrowId, int expectedRoomId, int expectedItemId, RoomTile userTile) {
        HabboItem entryArrow = room != null && entryArrowId > 0 ? room.getHabboItem(entryArrowId) : null;
        if (isTeleportTile(entryArrow)) {
            return entryArrow;
        }

        return findEntryArrow(room, expectedRoomId, expectedItemId, userTile);
    }

    private static void cacheTeleportDestination(Ride ride, Room room, HabboItem exitArrow) {
        ride.expectedNextRoomId = 0;
        ride.expectedEntryItemId = 0;

        if (exitArrow instanceof InteractionTeleport teleport
                && teleport.getTargetRoomId() > 0
                && teleport.getTargetId() > 0) {
            ride.expectedNextRoomId = teleport.getTargetRoomId();
            ride.expectedEntryItemId = teleport.getTargetId();
            return;
        }
        if (exitArrow instanceof InteractionTeleports teleport
                && teleport.getTargetRoomId() > 0
                && teleport.getTargetId() > 0) {
            ride.expectedNextRoomId = teleport.getTargetRoomId();
            ride.expectedEntryItemId = teleport.getTargetId();
            return;
        }

        int[] target = Emulator.getGameEnvironment().getItemManager().getTargetTeleportRoomId(exitArrow);
        if (target.length == 2 && target[0] != room.getId()) {
            ride.expectedNextRoomId = target[0];
            ride.expectedEntryItemId = target[1];
        }
    }

    private static void scheduleApproachCheck(Ride ride, long deadline) {
        Emulator.getThreading().run(() -> checkApproach(ride, deadline), APPROACH_POLL_INTERVAL_MS);
    }

    private static void checkApproach(Ride ride, long deadline) {
        boolean reached = false;
        boolean timedOut;
        synchronized (TaxiBotRideManager.class) {
            if (RIDES.get(ride.passengerId) != ride || ride.state != RideState.APPROACHING) {
                return;
            }

            Room room = ride.passenger.getHabboInfo().getCurrentRoom();
            RoomUnit botUnit = ride.bot == null ? null : ride.bot.getRoomUnit();
            RoomUnit passengerUnit = ride.passenger.getRoomUnit();
            if (room == null || botUnit == null || passengerUnit == null || ride.bot.getRoom() != room) {
                return;
            }

            RoomTile botTile = botUnit.getCurrentLocation();
            RoomTile passengerTile = passengerUnit.getCurrentLocation();
            reached = botTile != null && botTile.equals(ride.approachTile) && !botUnit.isWalking();
            timedOut = !reached && System.nanoTime() >= deadline;

            if (reached) {
                botUnit.setRotation(RoomUserRotation.fromValue(
                        Rotation.Calculate(botTile.x, botTile.y, passengerTile.x, passengerTile.y)));
                room.sendComposer(new RoomUserStatusComposer(botUnit).compose());
                ride.state = RideState.ACTIVE;
            } else if (!botUnit.isWalking() && !ride.approachTile.equals(botUnit.getGoal())) {
                botUnit.setGoalLocation(ride.approachTile);
            }
        }

        if (timedOut) {
            abort(ride, "El taxista no pudo llegar hasta ti. El recorrido fue cancelado.");
            return;
        }
        if (reached && !ride.route.isStarted() && !ride.route.start()) {
            abort(ride, "No se pudo iniciar el recorrido. El taxi fue cancelado.");
            return;
        }
        if (reached) {
            Room room = ride.passenger.getHabboInfo().getCurrentRoom();
            if (ride.passenger.getRoomUnit() != null) {
                ride.passenger.getRoomUnit().setCanWalk(true);
                ride.passenger.getRoomUnit().setFastWalk(true);
            }
            if (room != null) {
                room.giveEffect(ride.passenger, ride.taxiEffect, -1);
            }
            ride.passengerRp.setEnable(ride.taxiEffect);
        }
        if (!reached) {
            scheduleApproachCheck(ride, deadline);
        }
    }

    private static RoomTile findTileBehindBot(Room room, RoomUnit botUnit, RoomTile target) {
        RoomTile botTile = botUnit.getCurrentLocation();
        if (botTile == null) {
            return null;
        }

        int direction = Rotation.Calculate(botTile.x, botTile.y, target.x, target.y);
        RoomTile behind = room.getLayout().getTileInFront(botTile, (direction + 4) % 8);
        if (isAvailableSpawnTile(room, behind)) {
            return behind;
        }

        RoomTile previous = botUnit.getPreviousLocation();
        return previous != null && isAvailableSpawnTile(room, previous) ? previous : null;
    }

    private static boolean isWalkableTile(RoomTile tile) {
        return tile != null
                && tile.isWalkable()
                && tile.state != RoomTileState.INVALID
                && tile.state != RoomTileState.BLOCKED;
    }

    private static void ensureBotInDestinationRoom(Ride ride, Room room, RoomUnit passengerUnit) {
        if (ride.bot == null || (ride.bot.getRoom() == room && ride.bot.getRoomUnit() != null)) {
            return;
        }

        HabboItem entryArrow = findEntryArrow(ride, room);
        RoomTile spawnTile = findArrowSpawnTile(room, entryArrow, passengerUnit);
        if (spawnTile == null) {
            return;
        }

        removeBot(ride.bot);
        addBotToRoom(ride.bot, room, spawnTile, ride.taxiEffect, ride.botRotation);
        ride.destinationEntryArrowId = entryArrow.getId();
        if (ride.lastRoom != room) {
            ride.previousRoom = ride.lastRoom;
            ride.lastRoom = room;
        }
        ride.botInTransit = false;
    }

    private static void pollDestinationWalk(Ride ride, RoomTile target, long deadline) {
        boolean arrived;
        boolean timedOut;
        synchronized (TaxiBotRideManager.class) {
            if (RIDES.get(ride.passengerId) != ride || ride.state != RideState.EXITING) {
                return;
            }

            RoomUnit unit = ride.bot == null ? null : ride.bot.getRoomUnit();
            arrived = unit == null
                    || unit.getRoom() != ride.passenger.getHabboInfo().getCurrentRoom()
                    || (!unit.isWalking() && target.equals(unit.getCurrentLocation()));
            timedOut = !arrived && System.nanoTime() >= deadline;
        }

        if (arrived || timedOut) {
            if (timedOut) {
                LOGGER.warn("Taxi bot {} did not finish its destination walk before the timeout.", ride.passengerId);
            }
            scheduleDestinationCompletion(ride);
            return;
        }

        Emulator.getThreading().run(() -> pollDestinationWalk(ride, target, deadline), DESTINATION_POLL_INTERVAL_MS);
    }

    private static void scheduleDestinationCompletion(Ride ride) {
        Emulator.getThreading().run(() -> completePassengerAtDestination(ride), DESTINATION_PAUSE_MS);
    }

    private static void completePassengerAtDestination(Ride ride) {
        synchronized (TaxiBotRideManager.class) {
            if (RIDES.get(ride.passengerId) != ride || ride.state != RideState.EXITING) {
                return;
            }
            RIDES.remove(ride.passengerId, ride);
            ride.state = RideState.DEPARTING;
            restorePassenger(ride);
        }

        RoleplayWebsocketsRoomsInfo.getControllerRecorridosTaxi().completeRecorridoTaxi(ride.passenger);
        departBot(ride);
    }

    private static void departBot(Ride ride) {
        Room room = ride.passenger.getHabboInfo().getCurrentRoom();
        RoomTile arrowTile = null;
        synchronized (TaxiBotRideManager.class) {
            if (ride.state != RideState.DEPARTING) {
                return;
            }

            Bot bot = ride.bot;
            RoomUnit botUnit = bot == null ? null : bot.getRoomUnit();
            if (room != null && bot != null && bot.getRoom() == room && botUnit != null) {
                RoomTile userTile = ride.passenger.getRoomUnit() == null
                        ? null
                        : ride.passenger.getRoomUnit().getCurrentLocation();
                HabboItem entryArrow = findDepartureArrow(
                        room,
                        ride.destinationEntryArrowId,
                        ride.expectedNextRoomId,
                        ride.expectedEntryItemId,
                        userTile);

                if (entryArrow != null && room.getLayout() != null) {
                    arrowTile = room.getLayout().getTile(entryArrow.getX(), entryArrow.getY());
                    if (arrowTile != null) {
                        bot.stopFollowingHabbo();
                        botUnit.setCanWalk(true);
                        botUnit.setGoalLocation(arrowTile);
                        room.sendComposer(new RoomUserStatusComposer(botUnit).compose());
                    }
                }

                if (arrowTile != null && arrowTile.equals(botUnit.getCurrentLocation())) {
                    finishBotDeparture(ride);
                    return;
                }
            }
        }

        if (arrowTile == null) {
            finishBotDeparture(ride);
            return;
        }

        RoomTile target = arrowTile;
        long deadline = System.nanoTime() + BOT_DEPARTURE_TIMEOUT_NS;
        Emulator.getThreading().run(() -> pollBotDeparture(ride, room, target, deadline), DESTINATION_POLL_INTERVAL_MS);
    }

    private static void pollBotDeparture(Ride ride, Room room, RoomTile target, long deadline) {
        boolean arrived;
        boolean timedOut;
        synchronized (TaxiBotRideManager.class) {
            if (ride.state != RideState.DEPARTING) {
                return;
            }

            RoomUnit unit = ride.bot == null ? null : ride.bot.getRoomUnit();
            arrived = unit == null
                    || unit.getRoom() != room
                    || (!unit.isWalking() && target.equals(unit.getCurrentLocation()));
            timedOut = !arrived && System.nanoTime() >= deadline;
        }

        if (arrived || timedOut) {
            if (timedOut) {
                LOGGER.warn("Taxi bot {} did not reach its entry arrow before the timeout.", ride.passengerId);
            }
            finishBotDeparture(ride);
            return;
        }

        Emulator.getThreading().run(() -> pollBotDeparture(ride, room, target, deadline), DESTINATION_POLL_INTERVAL_MS);
    }

    private static void finishBotDeparture(Ride ride) {
        synchronized (TaxiBotRideManager.class) {
            if (ride.state == RideState.DEPARTING) {
                RIDES.remove(ride.passengerId, ride);
                ride.state = RideState.COMPLETE;
                if (ride.bot != null) {
                    ride.bot.stopFollowingHabbo();
                    removeBot(ride.bot);
                }
            }
        }
    }

    private static void abort(Ride ride, String message) {
        synchronized (TaxiBotRideManager.class) {
            if (RIDES.remove(ride.passengerId, ride)) {
                ride.state = RideState.CANCELLED;
                cleanup(ride);
            }
        }
        ride.passenger.whisper(message, RoomChatMessageBubbles.NORMAL);
        RoleplayWebsocketsRoomsInfo.getControllerRecorridosTaxi().eventFinaliceRecorrido(ride.passenger);
    }

    private static void cleanup(Ride ride) {
        if (ride.bot != null) {
            ride.bot.stopFollowingHabbo();
            removeBot(ride.bot);
        }

        restorePassenger(ride);
    }

    private static void restorePassenger(Ride ride) {
        Room room = ride.passenger.getHabboInfo().getCurrentRoom();
        if (ride.passenger.getRoomUnit() != null) {
            ride.passenger.getRoomUnit().setCanWalk(ride.passengerCouldWalk);
            ride.passenger.getRoomUnit().setFastWalk(ride.passengerFastWalk);
        }
        ride.passengerRp.setEnable(ride.passengerEnable);
        if (room != null && ride.passenger.getRoomUnit() != null) {
            room.giveEffect(ride.passenger, ride.passengerEffect, -1);
        }
    }

    private static void removeBot(Bot bot) {
        if (bot != null && bot.getRoom() != null) {
            bot.getRoom().getUnitManager().removeBot(bot);
        }
    }

    static boolean canFinishAtDestination(RideState state) {
        return state == RideState.ACTIVE || state == RideState.APPROACHING;
    }

    enum RideState {
        WAITING,
        APPROACHING,
        ACTIVE,
        EXITING,
        DEPARTING,
        COMPLETE,
        CANCELLED
    }

    private static final class Ride {
        private final int passengerId;
        private final Habbo passenger;
        private final RecorridoTaxi route;
        private final HabboRp passengerRp;
        private final boolean passengerCouldWalk;
        private final boolean passengerFastWalk;
        private final int passengerEffect;
        private final int passengerEnable;
        private final int taxiEffect;
        private Room lastRoom;
        private Room previousRoom;
        private RoomTile approachTile;
        private int targetArrowId;
        private int expectedNextRoomId;
        private int expectedEntryItemId;
        private int destinationEntryArrowId;
        private RoomUserRotation botRotation = RoomUserRotation.EAST;
        private boolean botInTransit;
        private Bot bot;
        private RideState state = RideState.WAITING;

        private Ride(Habbo passenger, RecorridoTaxi route, HabboRp passengerRp, Room room) {
            this.passengerId = passenger.getHabboInfo().getId();
            this.passenger = passenger;
            this.route = route;
            this.passengerRp = passengerRp;
            this.passengerCouldWalk = passenger.getRoomUnit().canWalk();
            this.passengerFastWalk = passenger.getRoomUnit().isFastWalk();
            this.passengerEffect = passenger.getRoomUnit().getEffectId();
            this.passengerEnable = passengerRp.getEnable();
            this.taxiEffect = RoleplayWebsocketsRoomsInfo.getConfig().getInt("enable_taxi_id");
            this.lastRoom = room;
        }
    }
}
