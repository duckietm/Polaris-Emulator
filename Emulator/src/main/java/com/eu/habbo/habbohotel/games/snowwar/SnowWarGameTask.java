package com.eu.habbo.habbohotel.games.snowwar;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.games.snowwar.events.SnowWarAvatarMoveEvent;
import com.eu.habbo.habbohotel.games.snowwar.events.SnowWarDeleteObjectEvent;
import com.eu.habbo.habbohotel.games.snowwar.events.SnowWarGameEvent;
import com.eu.habbo.habbohotel.games.snowwar.events.SnowWarHitEvent;
import com.eu.habbo.habbohotel.games.snowwar.events.SnowWarMachineAddSnowballEvent;
import com.eu.habbo.habbohotel.games.snowwar.events.SnowWarMachineTransferSnowballEvent;
import com.eu.habbo.habbohotel.games.snowwar.events.SnowWarRayGunBurstEvent;
import com.eu.habbo.habbohotel.games.snowwar.events.SnowWarStunEvent;
import com.eu.habbo.habbohotel.games.snowwar.events.SnowWarTreeHitEvent;
import com.eu.habbo.habbohotel.games.snowwar.mapping.SnowWarItem;
import com.eu.habbo.habbohotel.games.snowwar.mapping.SnowWarMap;
import com.eu.habbo.habbohotel.games.snowwar.objects.SnowWarAvatarObject;
import com.eu.habbo.habbohotel.games.snowwar.objects.SnowWarMachineObject;
import com.eu.habbo.habbohotel.games.snowwar.objects.SnowWarPileObject;
import com.eu.habbo.habbohotel.games.snowwar.objects.SnowWarSnowballObject;
import com.eu.habbo.habbohotel.games.snowwar.objects.SnowWarTreeObject;
import com.eu.habbo.messages.outgoing.snowwar.SnowStormGameStatusComposer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The main game loop: runs every 150ms, simulates 3 subturns of 50ms and
 * broadcasts a single GAMESTATUS packet per tick (README 7.5).
 */
public class SnowWarGameTask implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(SnowWarGameTask.class);

    private final SnowWarGame game;

    private int currentTurn = 0;
    private int currentChecksum = 0;

    /**
     * Active projectiles. Accessed from packet handler threads too - always
     * synchronize on the list itself (README 15.1).
     */
    private final List<SnowWarSnowballObject> snowballs = new ArrayList<>();

    /**
     * Events queued by packet handlers between ticks (throws, snowball
     * creation). Drained into subturn 0 of the next tick.
     */
    private final ConcurrentLinkedQueue<SnowWarGameEvent> pendingEvents = new ConcurrentLinkedQueue<>();

    private List<List<SnowWarGameEvent>> turnEventsList = new ArrayList<>();

    /**
     * Last burst time per ray gun item. Only touched from the game tick thread.
     * Keeps arrival-triggered bursts from being spammed by hopping on/off the
     * use tile.
     */
    private final Map<SnowWarItem, Long> rayGunLastFired = new IdentityHashMap<>();

    public SnowWarGameTask(SnowWarGame game) {
        this.game = game;
    }

    public int getCurrentTurn() {
        return this.currentTurn;
    }

    public int getCurrentChecksum() {
        return this.currentChecksum;
    }

    public void addSnowball(SnowWarSnowballObject snowball) {
        synchronized (this.snowballs) {
            this.snowballs.add(snowball);
        }
    }

    public List<SnowWarSnowballObject> getSnowballsSnapshot() {
        synchronized (this.snowballs) {
            return new ArrayList<>(this.snowballs);
        }
    }

    public void queueEvent(SnowWarGameEvent event) {
        this.pendingEvents.add(event);
    }

    @Override
    public synchronized void run() {
        if (this.game.getState() != SnowWarGameState.RUNNING) {
            return;
        }

        try {
            // Match timer.
            if (this.game.getTotalSecondsLeft() <= 0) {
                this.game.endGame();
                return;
            }

            // Treat disconnected players as having left the arena.
            for (SnowWarGamePlayer player : this.game.getActivePlayers()) {
                if (player.getHabbo().getClient() == null
                        || Emulator.getGameEnvironment().getHabboManager().getHabbo(player.getUserId()) == null) {
                    this.game.exitGame(player.getUserId());
                }
            }

            if (this.game.getState() != SnowWarGameState.RUNNING) {
                return;
            }

            // Queue movement events for players who are walking.
            for (SnowWarGamePlayer player : this.game.getActivePlayers()) {
                SnowWarAttributes attr = player.getAttributes();
                if (attr.isWalking()) {
                    this.queueMovementEvent(player, attr);
                }
            }

            this.processSubturns();

            this.game.broadcast(
                    new SnowStormGameStatusComposer(this.currentTurn, this.currentChecksum, this.turnEventsList));
        } catch (Exception e) {
            LOGGER.error("Error in SnowWar game " + this.game.getId() + " tick.", e);
        } finally {
            if (this.game.getState() == SnowWarGameState.RUNNING) {
                Emulator.getThreading().run(this, SnowWarConstants.SERVER_TICK_MS);
            }
        }
    }

    private void queueMovementEvent(SnowWarGamePlayer player, SnowWarAttributes attr) {
        int goalWorldX;
        int goalWorldY;

        SnowWarPoint goalWorld = attr.getGoalWorldCoordinates();
        if (goalWorld != null) {
            goalWorldX = goalWorld.getX();
            goalWorldY = goalWorld.getY();
        } else if (attr.getWalkGoal() != null) {
            goalWorldX = SnowWarMath.tileToWorld(attr.getWalkGoal().getX());
            goalWorldY = SnowWarMath.tileToWorld(attr.getWalkGoal().getY());
        } else {
            return;
        }

        this.pendingEvents.add(new SnowWarAvatarMoveEvent(player.getObjectId(), goalWorldX, goalWorldY));
    }

    private void processSubturns() {
        this.turnEventsList = new ArrayList<>();
        this.currentTurn++;

        List<SnowWarDelayedEvent> delayedCollisionEvents = new ArrayList<>();
        List<SnowWarMachineObject> machinesPendingSnowball = new ArrayList<>();
        List<MachinePickup> machinePickups = new ArrayList<>();
        List<PilePickup> pilePickups = new ArrayList<>();

        // Build the AIR subturn event lists; drain handler events into subturn 0.
        for (int i = 0; i < SnowWarConstants.SUBTURNS_PER_TICK; i++) {
            this.turnEventsList.add(new ArrayList<>());
        }

        SnowWarGameEvent pending;
        while ((pending = this.pendingEvents.poll()) != null) {
            this.turnEventsList.get(0).add(pending);
        }

        List<SnowWarGamePlayer> activePlayers = this.game.getActivePlayers();

        for (int subturnIndex = 0; subturnIndex < SnowWarConstants.SUBTURNS_PER_TICK; subturnIndex++) {
            List<SnowWarGameEvent> subturnEvents = this.turnEventsList.get(subturnIndex);

            List<SnowWarSnowballObject> snowballSnapshot = this.getSnowballsSnapshot();
            List<SnowWarGamePlayer> reachedDestinations = new ArrayList<>();

            // === PHASE 1: Move all avatars ===
            for (SnowWarGamePlayer player : activePlayers) {
                SnowWarAvatarObject avatar = player.getAvatar();
                if (avatar == null) {
                    continue;
                }

                if (avatar.calculateFrameMovement()) {
                    reachedDestinations.add(player);
                }
            }

            for (SnowWarGamePlayer player : reachedDestinations) {
                this.tryFireRayGun(player, snowballSnapshot, subturnEvents);
            }

            // === PHASE 2: Move, then collide exactly like AIR ===
            for (SnowWarSnowballObject ball : snowballSnapshot) {
                if (!ball.isAlive()) {
                    continue; // Already consumed by a hit this tick.
                }

                ball.calculateFrameMovement();

                if (this.checkObjectCollision(ball, activePlayers, subturnEvents, delayedCollisionEvents)
                        || ball.hasFloorCollision()) {
                    this.removeSnowball(ball, subturnEvents);
                }
            }

            // === PHASE 3: Process snowball machines ===
            for (SnowWarMachineObject machine : this.game.getMachines()) {
                if (machine.processGeneratorTick()) {
                    subturnEvents.add(new SnowWarMachineAddSnowballEvent(machine.getObjectId()));
                    machinesPendingSnowball.add(machine);
                }
            }

            // === PHASE 4: Auto-collect from machine and pile pickup tiles ===
            for (SnowWarGamePlayer player : activePlayers) {
                SnowWarAttributes attr = player.getAttributes();
                if (!attr.tickSnowballPickupTimer()) {
                    continue;
                }
                boolean pickedUp = false;
                for (SnowWarMachineObject machine : this.game.getMachines()) {
                    if (machine.canPlayerPickup(attr)) {
                        machine.reservePickup();
                        attr.resetSnowballPickupTimer();
                        subturnEvents.add(
                                new SnowWarMachineTransferSnowballEvent(player.getObjectId(), machine.getObjectId()));
                        machinePickups.add(new MachinePickup(machine, attr));
                        pickedUp = true;
                        break;
                    }
                }
                if (pickedUp) {
                    continue;
                }
                for (SnowWarPileObject pile : this.game.getPiles()) {
                    if (pile.canPlayerPickup(attr)) {
                        pile.reservePickup();
                        attr.resetSnowballPickupTimer();
                        subturnEvents.add(
                                new SnowWarMachineTransferSnowballEvent(player.getObjectId(), pile.getObjectId()));
                        pilePickups.add(new PilePickup(pile, attr));
                        break;
                    }
                }
            }
        }

        // === PHASE 5: Checksum BEFORE applying deferred state (README 15.2) ===
        this.currentChecksum =
                SnowWarChecksumCalculator.calculate(this.game, this.getSnowballsSnapshot(), this.currentTurn);

        // === PHASE 6: Apply deferred snowball-source events ===
        for (SnowWarMachineObject machine : machinesPendingSnowball) {
            machine.addSnowball();
        }
        for (MachinePickup pickup : machinePickups) {
            pickup.machine().transferReservedSnowballTo(pickup.attributes());
        }
        for (PilePickup pickup : pilePickups) {
            pickup.pile().transferReservedSnowballTo(pickup.attributes());
        }

        // === PHASE 7: Apply collision results (hits, stuns) ===
        this.processDelayedCollisions(delayedCollisionEvents);
    }

    private record MachinePickup(SnowWarMachineObject machine, SnowWarAttributes attributes) {}

    private record PilePickup(SnowWarPileObject pile, SnowWarAttributes attributes) {}

    private void tryFireRayGun(
            SnowWarGamePlayer player,
            List<SnowWarSnowballObject> snowballSnapshot,
            List<SnowWarGameEvent> subturnEvents) {
        SnowWarAttributes attr = player.getAttributes();
        SnowWarItem rayGun = findUsableRayGun(this.game.getMap(), attr);
        if (rayGun == null) {
            return;
        }

        long now = System.currentTimeMillis();
        Long lastFired = this.rayGunLastFired.get(rayGun);
        if (lastFired != null && (now - lastFired) < SnowWarConstants.RAY_GUN_COOLDOWN_MS) {
            return;
        }
        this.rayGunLastFired.put(rayGun, now);

        attr.setRotation(Math.floorMod(rayGun.getRotation(), 8));
        SnowWarPoint[] targets = getRayGunBurstTargets(rayGun);
        int firstObjectId = this.game.reserveObjectIds(targets.length);
        for (int index = 0; index < targets.length; index++) {
            SnowWarPoint target = targets[index];
            SnowWarSnowballObject snowball = new SnowWarSnowballObject(
                    firstObjectId + index,
                    this.game.getMap(),
                    player,
                    attr.getWorldPosition().getX(),
                    attr.getWorldPosition().getY(),
                    SnowWarMath.tileToWorld(target.getX()),
                    SnowWarMath.tileToWorld(target.getY()),
                    SnowWarConstants.TRAJECTORY_DEFAULT);
            this.addSnowball(snowball);
            snowballSnapshot.add(snowball);
        }

        attr.setLastThrowTime(System.currentTimeMillis());
        subturnEvents.add(new SnowWarRayGunBurstEvent(
                firstObjectId,
                player.getObjectId(),
                SnowWarMath.tileToWorld(targets[0].getX()),
                SnowWarMath.tileToWorld(targets[0].getY()),
                SnowWarConstants.TRAJECTORY_DEFAULT));
    }

    static SnowWarItem findUsableRayGun(SnowWarMap map, SnowWarAttributes attr) {
        for (SnowWarItem item : map.getItems()) {
            if (!"ads_igorraygun".equals(item.getName())) {
                continue;
            }

            int rotation = Math.floorMod(item.getRotation(), 8);
            int dx = rotation == 2 ? 1 : rotation == 6 ? -1 : 0;
            int dy = rotation == 4 ? 1 : rotation == 0 ? -1 : 0;
            if (dx == 0 && dy == 0) {
                continue;
            }

            int useX = item.getX() + (dx < 0 ? item.getEffectiveWidth() : dx > 0 ? -1 : 0);
            int useY = item.getY() + (dy < 0 ? item.getEffectiveLength() : dy > 0 ? -1 : 0);
            if (attr.getCurrentPosition().equals(new SnowWarPoint(useX, useY))) {
                return item;
            }
        }
        return null;
    }

    static SnowWarPoint[] getRayGunBurstTargets(SnowWarItem rayGun) {
        int rotation = Math.floorMod(rayGun.getRotation(), 8);
        int dx = rotation == 2 ? 1 : rotation == 6 ? -1 : 0;
        int dy = rotation == 4 ? 1 : rotation == 0 ? -1 : 0;
        int x = rayGun.getX() + (dx * 15);
        int y = rayGun.getY() + (dy * 15);
        return new SnowWarPoint[] {
            new SnowWarPoint(x, y),
            new SnowWarPoint(x, y + 1),
            new SnowWarPoint(x + 1, y),
            new SnowWarPoint(x - 1, y + 1),
            new SnowWarPoint(x - 1, y - 1),
            new SnowWarPoint(x + 1, y - 1),
            new SnowWarPoint(x + 1, y + 1)
        };
    }

    /** AIR tests the current tile, then forward, forward-left and forward-right. */
    private boolean checkObjectCollision(
            SnowWarSnowballObject ball,
            List<SnowWarGamePlayer> activePlayers,
            List<SnowWarGameEvent> subturnEvents,
            List<SnowWarDelayedEvent> delayedEvents) {
        for (SnowWarPoint tile : getCollisionTiles(ball)) {
            for (SnowWarTreeObject tree : this.game.getTrees()) {
                if (tree.getX() == tile.getX() && tree.getY() == tile.getY() && tree.testCollision(ball)) {
                    subturnEvents.add(new SnowWarTreeHitEvent(tree.getX(), tree.getY(), tree.hit()));
                    return true;
                }
            }

            for (SnowWarMachineObject machine : this.game.getMachines()) {
                if (machine.getX() == tile.getX() && machine.getY() == tile.getY() && machine.testCollision(ball)) {
                    return true;
                }
            }

            for (SnowWarGamePlayer player : activePlayers) {
                SnowWarAvatarObject avatar = player.getAvatar();
                SnowWarAttributes attr = player.getAttributes();
                if (avatar == null || !attr.getCurrentPosition().equals(tile) || !avatar.testCollision(ball)) {
                    continue;
                }

                if (this.game.isOpponent(player, ball.getThrower())) {
                    this.applyAvatarHit(player, ball, subturnEvents, delayedEvents);
                }
                return true;
            }
        }
        return false;
    }

    private static SnowWarPoint[] getCollisionTiles(SnowWarSnowballObject ball) {
        int currentX = SnowWarMath.worldToTile(ball.getLocH());
        int currentY = SnowWarMath.worldToTile(ball.getLocV());
        int direction = SnowWarMath.direction360To8(ball.getDirection());
        int[] offsetX = {0, 1, 1, 1, 0, -1, -1, -1};
        int[] offsetY = {-1, -1, 0, 1, 1, 1, 0, -1};
        int left = Math.floorMod(direction - 1, 8);
        int right = Math.floorMod(direction + 1, 8);
        return new SnowWarPoint[] {
            new SnowWarPoint(currentX, currentY),
            new SnowWarPoint(currentX + offsetX[direction], currentY + offsetY[direction]),
            new SnowWarPoint(currentX + offsetX[left], currentY + offsetY[left]),
            new SnowWarPoint(currentX + offsetX[right], currentY + offsetY[right])
        };
    }

    private void applyAvatarHit(
            SnowWarGamePlayer player,
            SnowWarSnowballObject ball,
            List<SnowWarGameEvent> subturnEvents,
            List<SnowWarDelayedEvent> delayedEvents) {
        SnowWarAttributes attr = player.getAttributes();
        if (attr.getPendingHealth() > 1) {
            attr.setPendingHealth(attr.getPendingHealth() - 1);
            subturnEvents.add(
                    new SnowWarHitEvent(ball.getThrower().getObjectId(), player.getObjectId(), ball.getDirection()));
            delayedEvents.add(new SnowWarDelayedEvent(SnowWarDelayedEventType.HIT, player, ball));
        } else if (attr.getPendingHealth() == 1 && !attr.isPendingStun()) {
            attr.setPendingHealth(0);
            attr.setPendingStun(true);
            subturnEvents.add(
                    new SnowWarHitEvent(ball.getThrower().getObjectId(), player.getObjectId(), ball.getDirection()));
            subturnEvents.add(
                    new SnowWarStunEvent(player.getObjectId(), ball.getThrower().getObjectId(), ball.getDirection()));
            delayedEvents.add(new SnowWarDelayedEvent(SnowWarDelayedEventType.HIT, player, ball));
            delayedEvents.add(new SnowWarDelayedEvent(SnowWarDelayedEventType.STUN, player, ball));
        }
    }

    private void removeSnowball(SnowWarSnowballObject ball, List<SnowWarGameEvent> subturnEvents) {
        if (!ball.isAlive()) {
            return;
        }
        ball.kill();
        synchronized (this.snowballs) {
            this.snowballs.remove(ball);
        }
        subturnEvents.add(new SnowWarDeleteObjectEvent(
                ball.getObjectId(), ball.getLocH(), ball.getLocV(), ball.getHeight(), ball.getTrajectory()));
    }

    private void processDelayedCollisions(List<SnowWarDelayedEvent> events) {
        for (SnowWarDelayedEvent event : events) {
            SnowWarAttributes attr = event.getPlayer().getAttributes();
            SnowWarAttributes throwerAttr = event.getBall().getThrower().getAttributes();

            switch (event.getType()) {
                case HIT:
                    attr.getHealth().set(attr.getPendingHealth());
                    throwerAttr.getScore().addAndGet(SnowWarConstants.HIT_SCORE);
                    break;

                case STUN:
                    if (event.getPlayer().getAvatar() != null) {
                        event.getPlayer().getAvatar().stopWalking();
                    }

                    attr.setActivityState(SnowWarActivityState.STUNNED);
                    attr.setActivityTimer(SnowWarConstants.STUNNED_TIMER);
                    attr.setRotation(Math.floorMod(
                            SnowWarMath.direction360To8(event.getBall().getDirection()) + 4, 8));

                    throwerAttr.getScore().addAndGet(SnowWarConstants.STUN_SCORE);
                    break;

                default:
                    break;
            }
        }
    }
}
