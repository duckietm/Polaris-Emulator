package com.eu.habbo.habbohotel.games.snowwar;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.games.snowwar.events.SnowWarAvatarMoveEvent;
import com.eu.habbo.habbohotel.games.snowwar.events.SnowWarDeleteObjectEvent;
import com.eu.habbo.habbohotel.games.snowwar.events.SnowWarGameEvent;
import com.eu.habbo.habbohotel.games.snowwar.events.SnowWarHitEvent;
import com.eu.habbo.habbohotel.games.snowwar.events.SnowWarMachineAddSnowballEvent;
import com.eu.habbo.habbohotel.games.snowwar.events.SnowWarMachineTransferSnowballEvent;
import com.eu.habbo.habbohotel.games.snowwar.events.SnowWarStunEvent;
import com.eu.habbo.habbohotel.games.snowwar.objects.SnowWarAvatarObject;
import com.eu.habbo.habbohotel.games.snowwar.objects.SnowWarMachineObject;
import com.eu.habbo.habbohotel.games.snowwar.objects.SnowWarSnowballObject;
import com.eu.habbo.messages.outgoing.snowwar.SnowStormGameStatusComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The main game loop: runs every 300ms, simulates 5 subturns of 60ms and
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

            this.game.broadcast(new SnowStormGameStatusComposer(this.currentTurn, this.currentChecksum, this.turnEventsList));
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

        // Build the 5 subturn event lists; drain handler events into subturn 0.
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

            // === PHASE 1: Move all avatars + collision detection ===
            for (SnowWarGamePlayer player : activePlayers) {
                SnowWarAvatarObject avatar = player.getAvatar();
                if (avatar == null) {
                    continue;
                }

                avatar.calculateFrameMovement();

                this.checkCollisions(player, avatar, snowballSnapshot, subturnEvents, delayedCollisionEvents);
            }

            // === PHASE 2: Move all snowballs ===
            for (SnowWarSnowballObject ball : snowballSnapshot) {
                if (!ball.isAlive()) {
                    continue; // Already consumed by a hit this tick.
                }

                ball.calculateFrameMovement();

                if (!ball.isAlive()) {
                    synchronized (this.snowballs) {
                        this.snowballs.remove(ball);
                    }
                    subturnEvents.add(new SnowWarDeleteObjectEvent(ball.getObjectId()));
                }
            }

            // === PHASE 3: Process snowball machines ===
            for (SnowWarMachineObject machine : this.game.getMachines()) {
                if (machine.processGeneratorTick()) {
                    subturnEvents.add(new SnowWarMachineAddSnowballEvent(machine.getObjectId()));
                    machinesPendingSnowball.add(machine);
                }
            }
        }

        // === PHASE 4: Checksum BEFORE applying deferred state (README 15.2) ===
        this.currentChecksum = SnowWarChecksumCalculator.calculate(this.game, this.getSnowballsSnapshot(), this.currentTurn);

        // === PHASE 5: Apply deferred machine snowballs ===
        for (SnowWarMachineObject machine : machinesPendingSnowball) {
            machine.addSnowball();
        }

        // === PHASE 6: Process machine pickups ===
        for (SnowWarGamePlayer player : activePlayers) {
            SnowWarAttributes attr = player.getAttributes();

            for (SnowWarMachineObject machine : this.game.getMachines()) {
                if (machine.canPlayerPickup(attr)) {
                    this.turnEventsList.get(0).add(new SnowWarMachineTransferSnowballEvent(
                            player.getObjectId(), machine.getObjectId()));
                    machine.transferSnowballTo(attr);
                    break;
                }
            }
        }

        // === PHASE 7: Apply collision results (hits, stuns) ===
        this.processDelayedCollisions(delayedCollisionEvents);
    }

    /**
     * Detects snowball hits on an avatar. Wire events are queued to the
     * current subturn, state changes are deferred (README 10, 12.3).
     */
    private void checkCollisions(SnowWarGamePlayer player, SnowWarAvatarObject avatar,
                                 List<SnowWarSnowballObject> snowballSnapshot,
                                 List<SnowWarGameEvent> subturnEvents,
                                 List<SnowWarDelayedEvent> delayedEvents) {
        if (snowballSnapshot.isEmpty()) {
            return;
        }

        if (avatar.isImmune()) {
            return;
        }

        SnowWarAttributes attr = player.getAttributes();

        for (SnowWarSnowballObject ball : snowballSnapshot) {
            if (!ball.isAlive()) {
                continue;
            }

            if (!avatar.testCollision(ball)) {
                continue;
            }

            if (attr.getPendingHealth() > 0) {
                attr.setPendingHealth(attr.getPendingHealth() - 1);
                this.removeSnowball(ball);
                subturnEvents.add(new SnowWarHitEvent(
                        ball.getThrower().getObjectId(), player.getObjectId(), ball.getDirection()));
                delayedEvents.add(new SnowWarDelayedEvent(SnowWarDelayedEventType.HIT, player, ball));
            } else if (!attr.isPendingStun()) {
                attr.setPendingStun(true);
                this.removeSnowball(ball);
                subturnEvents.add(new SnowWarStunEvent(
                        player.getObjectId(), ball.getThrower().getObjectId(), ball.getDirection()));
                delayedEvents.add(new SnowWarDelayedEvent(SnowWarDelayedEventType.STUN, player, ball));
            }
        }
    }

    private void removeSnowball(SnowWarSnowballObject ball) {
        ball.kill();
        synchronized (this.snowballs) {
            this.snowballs.remove(ball);
        }
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
                    attr.getSnowballCount().set(0);

                    throwerAttr.getScore().addAndGet(SnowWarConstants.STUN_SCORE);
                    break;

                default:
                    break;
            }
        }
    }
}
