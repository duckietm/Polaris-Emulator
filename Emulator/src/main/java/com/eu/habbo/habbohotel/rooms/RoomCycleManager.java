package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.items.ICycleable;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.users.DanceType;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredMoveCarryHelper;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.rooms.RoomAccessDeniedComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUnitIdleComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserIgnoredComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;
import com.eu.habbo.plugin.events.users.UserExitRoomEvent;
import gnu.trove.iterator.TIntObjectIterator;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.procedure.TIntObjectProcedure;
import gnu.trove.set.hash.THashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the room cycle/tick logic.
 * Handles the periodic updates for habbos, bots, pets, and other room entities.
 */
public class RoomCycleManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomCycleManager.class);

    private final Room room;
    private boolean cycleOdd;
    private long cycleTimestamp;
    private int idleCycles;
    private int idleHostingCycles;
    private long rollerCycle = System.currentTimeMillis();

    public RoomCycleManager(Room room) {
        this.room = room;
        this.cycleOdd = false;
        this.cycleTimestamp = 0;
        this.idleCycles = 0;
        this.idleHostingCycles = 0;
    }

    /**
     * Gets the current cycle timestamp.
     */
    public long getCycleTimestamp() {
        return this.cycleTimestamp;
    }

    /**
     * Resets idle cycles when room becomes active.
     */
    public void resetIdleCycles() {
        this.idleCycles = 0;
    }

    /**
     * Main cycle method - called every 500ms.
     * Processes all room entities and scheduled tasks.
     */
    public void cycle() {
        this.cycleOdd = !this.cycleOdd;
        this.cycleTimestamp = System.currentTimeMillis();
        final boolean[] foundRightHolder = {false};

        boolean loaded = this.room.isLoaded();

        if (loaded) {
            processScheduledTasks();
            processCycleTasks();
            processDecoHosting();

            if (!this.room.getCurrentHabbos().isEmpty()) {
                this.idleCycles = 0;

                THashSet<RoomUnit> updatedUnit = new THashSet<>();
                ArrayList<Habbo> toKick = new ArrayList<>();

                final long millis = System.currentTimeMillis();

                // Process all habbos
                for (Habbo habbo : this.room.getCurrentHabbos().values()) {
                    if (!foundRightHolder[0]) {
                        foundRightHolder[0] = habbo.getRoomUnit().getRightsLevel() != RoomRightLevels.NONE;
                    }

                    processHabboHandItem(habbo, millis);
                    processHabboEffect(habbo, millis);
                    processHabboKick(habbo);
                    processHabboIdle(habbo, toKick);
                    processHabboMute(habbo);
                    processHabboChatCounter(habbo);

                    if (this.cycleRoomUnit(habbo.getRoomUnit(), RoomUnitType.USER)) {
                        updatedUnit.add(habbo.getRoomUnit());
                    }
                }

                // Kick idle habbos
                for (Habbo habbo : toKick) {
                    Emulator.getGameEnvironment().getRoomManager().leaveRoom(habbo, this.room);
                }

                // Process bots
                processBots(updatedUnit);

                // Process pets
                processPets(updatedUnit);

                // Process rollers
                processRollers(updatedUnit);

                // Send status updates
                if (!updatedUnit.isEmpty()) {
                    ServerMessage statusComposer = new RoomUserStatusComposer(updatedUnit, true).compose();
                    WiredMoveCarryHelper.beginMovementCollection();
                    WiredMoveCarryHelper.processUserFollowers(this.room, updatedUnit);
                    ServerMessage wiredMovementsComposer = WiredMoveCarryHelper.finishMovementCollection();

                    if (wiredMovementsComposer != null) {
                        ArrayList<ServerMessage> batchedMessages = new ArrayList<>(2);
                        batchedMessages.add(statusComposer);
                        batchedMessages.add(wiredMovementsComposer);
                        this.room.sendComposers(batchedMessages);
                    } else {
                        this.room.sendComposer(statusComposer);
                    }
                }

                // Cycle trax manager
                if (this.room.getTraxManager() != null) {
                    this.room.getTraxManager().cycle();
                }
            } else {
                // Room is empty - check for disposal
                if (this.idleCycles < 60) {
                    this.idleCycles++;
                } else {
                    this.room.dispose();
                }
            }
        }

        // Process habbo queue
        processHabboQueue(foundRightHolder[0]);

        // Send scheduled composers
        processScheduledComposers();
    }

    /**
     * Processes scheduled tasks.
     */
    private void processScheduledTasks() {
        Runnable task;
        while ((task = this.room.scheduledTasks.poll()) != null) {
            Emulator.getThreading().run(task);
        }
    }

    /**
     * Processes cycleable tasks.
     */
    private void processCycleTasks() {
        if (this.room.getRoomSpecialTypes() != null) {
            for (ICycleable task : this.room.getRoomSpecialTypes().getCycleTasks()) {
                task.cycle(this.room);
            }
        }
    }

    /**
     * Processes deco hosting achievement.
     */
    private void processDecoHosting() {
        if (Emulator.getConfig().getBoolean("hotel.rooms.deco_hosting")) {
            if (this.idleHostingCycles < 120) {
                this.idleHostingCycles++;
            } else {
                this.idleHostingCycles = 0;

                int amount = (int) this.room.getCurrentHabbos().values().stream()
                        .filter(habbo -> habbo.getHabboInfo().getId() != this.room.getOwnerId()).count();
                if (amount > 0) {
                    AchievementManager.progressAchievement(this.room.getOwnerId(),
                            Emulator.getGameEnvironment().getAchievementManager()
                                    .getAchievement("RoomDecoHosting"), amount);
                }
            }
        }
    }

    /**
     * Processes habbo hand item expiry.
     */
    private void processHabboHandItem(Habbo habbo, long millis) {
        if (Room.HAND_ITEM_TIME > 0 && habbo.getRoomUnit().getHandItem() > 0
                && millis - habbo.getRoomUnit().getHandItemTimestamp() > (Room.HAND_ITEM_TIME * 1000L)) {
            this.room.giveHandItem(habbo, 0);
        }
    }

    /**
     * Processes habbo effect expiry.
     */
    private void processHabboEffect(Habbo habbo, long millis) {
        if (habbo.getRoomUnit().getEffectId() > 0 && millis / 1000 > habbo.getRoomUnit().getEffectEndTimestamp()) {
            this.room.giveEffect(habbo, 0, -1);
        }
    }

    /**
     * Processes habbo kick status.
     */
    private void processHabboKick(Habbo habbo) {
        if (habbo.getRoomUnit().isKicked) {
            habbo.getRoomUnit().kickCount++;

            if (habbo.getRoomUnit().kickCount >= 5) {
                final Room room = this.room;
                this.room.scheduledTasks.add(
                        () -> Emulator.getGameEnvironment().getRoomManager().leaveRoom(habbo, room));
            }
        }
    }

    /**
     * Processes habbo idle status.
     */
    private void processHabboIdle(Habbo habbo, ArrayList<Habbo> toKick) {
        if (Emulator.getConfig().getBoolean("hotel.rooms.auto.idle")) {
            if (!habbo.getRoomUnit().isIdle()) {
                habbo.getRoomUnit().increaseIdleTimer();

                if (habbo.getRoomUnit().isIdle()) {
                    boolean danceIsNone = (habbo.getRoomUnit().getDanceType() == DanceType.NONE);
                    if (danceIsNone) {
                        this.room.sendComposer(new RoomUnitIdleComposer(habbo.getRoomUnit()).compose());
                    }
                    if (danceIsNone && !Emulator.getConfig()
                            .getBoolean("hotel.roomuser.idle.not_dancing.ignore.wired_idle")) {
                        WiredManager.triggerUserIdles(this.room, habbo.getRoomUnit());
                    }
                }
            } else {
                habbo.getRoomUnit().increaseIdleTimer();

                if (!this.room.isOwner(habbo)
                        && habbo.getRoomUnit().getIdleTimer() >= Room.IDLE_CYCLES_KICK) {
                    UserExitRoomEvent event = new UserExitRoomEvent(habbo,
                            UserExitRoomEvent.UserExitRoomReason.KICKED_IDLE);
                    Emulator.getPluginManager().fireEvent(event);

                    if (!event.isCancelled()) {
                        toKick.add(habbo);
                    }
                }
            }
        }
    }

    /**
     * Processes habbo mute status.
     */
    private void processHabboMute(Habbo habbo) {
        if (habbo.getHabboStats().mutedBubbleTracker && habbo.getHabboStats().allowTalk()) {
            habbo.getHabboStats().mutedBubbleTracker = false;
            this.room.sendComposer(
                    new RoomUserIgnoredComposer(habbo, RoomUserIgnoredComposer.UNIGNORED).compose());
        }
    }

    /**
     * Processes habbo chat counter.
     */
    private void processHabboChatCounter(Habbo habbo) {
        // Subtract 1 from the chatCounter every odd cycle, which is every (500ms * 2).
        if (this.cycleOdd && habbo.getHabboStats().chatCounter.get() > 0) {
            habbo.getHabboStats().chatCounter.decrementAndGet();
        }
    }

    /**
     * Processes all bots in the room.
     */
    private void processBots(THashSet<RoomUnit> updatedUnit) {
        TIntObjectMap<Bot> currentBots = this.room.getCurrentBots();
        if (currentBots.isEmpty()) {
            return;
        }

        // Snapshot under the map monitor (currentBots is a synchronizedMap whose
        // iterator isn't concurrency-safe), then cycle OFF-lock. Holding the
        // monitor across the whole tick would block bot place/pickup and room
        // dispose for the tick duration AND invert the lock order vs
        // roomUnitLock -> currentBots taken by RoomUnitManager.addBot/clear.
        final ArrayList<Bot> bots;
        synchronized (currentBots) {
            bots = new ArrayList<>(currentBots.valueCollection());
        }

        for (Bot bot : bots) {
            try {
                if (bot == null || bot.getRoomUnit() == null) {
                    continue;
                }

                if (!this.room.isAllowBotsWalk() && bot.getRoomUnit().isWalking()) {
                    bot.getRoomUnit().stopWalking();
                    updatedUnit.add(bot.getRoomUnit());
                    continue;
                }

                bot.cycle(this.room.isAllowBotsWalk());

                if (this.cycleRoomUnit(bot.getRoomUnit(), RoomUnitType.BOT)) {
                    updatedUnit.add(bot.getRoomUnit());
                }
            } catch (Exception e) {
                LOGGER.error("Caught exception", e);
            }
        }
    }

    /**
     * Processes all pets in the room.
     */
    private void processPets(THashSet<RoomUnit> updatedUnit) {
        TIntObjectMap<Pet> currentPets = this.room.getCurrentPets();
        if (currentPets.isEmpty() || !this.room.isAllowBotsWalk()) {
            return;
        }

        // Snapshot under the monitor, then cycle off-lock (see processBots): avoids
        // holding currentPets for the whole tick and the roomUnitLock inversion.
        final ArrayList<Pet> pets;
        synchronized (currentPets) {
            pets = new ArrayList<>(currentPets.valueCollection());
        }

        for (Pet pet : pets) {
            try {
                if (pet == null || pet.getRoomUnit() == null) {
                    continue;
                }

                if (this.cycleRoomUnit(pet.getRoomUnit(), RoomUnitType.PET)) {
                    updatedUnit.add(pet.getRoomUnit());
                }

                pet.cycle();

                if (pet.packetUpdate) {
                    updatedUnit.add(pet.getRoomUnit());
                    pet.packetUpdate = false;
                }

                if (pet.getRoomUnit().isWalking() && pet.getRoomUnit().getPath().size() == 1
                        && pet.getRoomUnit().hasStatus(RoomUnitStatus.GESTURE)) {
                    pet.getRoomUnit().removeStatus(RoomUnitStatus.GESTURE);
                    updatedUnit.add(pet.getRoomUnit());
                }
            } catch (Exception e) {
                LOGGER.error("Caught exception", e);
            }
        }
    }

    /**
     * Processes roller cycle.
     */
    private void processRollers(THashSet<RoomUnit> updatedUnit) {
        Integer controlledRollerSpeed = RoomQueueSpeedControlSupport.getEffectiveRollerSpeed(this.room);
        int rollerSpeed = (controlledRollerSpeed != null) ? controlledRollerSpeed : this.room.getRollerSpeed();
        if (rollerSpeed != -1 && this.rollerCycle >= rollerSpeed) {
            this.rollerCycle = 0;
            this.room.getRollerManager().processRollerCycle(updatedUnit, this.cycleTimestamp);
        } else {
            this.rollerCycle++;
        }
    }

    /**
     * Processes the habbo queue.
     */
    private void processHabboQueue(boolean foundRightHolder) {
        TIntObjectMap<Habbo> habboQueue = this.room.getHabboQueue();
        synchronized (habboQueue) {
            if (!habboQueue.isEmpty() && !foundRightHolder) {
                final Room room = this.room;
                habboQueue.forEachEntry(new TIntObjectProcedure<Habbo>() {
                    @Override
                    public boolean execute(int a, Habbo b) {
                        if (b.isOnline()) {
                            if (b.getHabboInfo().getRoomQueueId() == room.getId()) {
                                b.getClient().sendResponse(new RoomAccessDeniedComposer(""));
                            }
                        }
                        return true;
                    }
                });
                habboQueue.clear();
            }
        }
    }

    /**
     * Processes scheduled composers.
     */
    private void processScheduledComposers() {
        if (!this.room.scheduledComposers.isEmpty()) {
            for (ServerMessage message : this.room.scheduledComposers) {
                this.room.sendComposer(message);
            }
            this.room.scheduledComposers.clear();
        }
    }

    /**
     * Cycles a room unit (handles movement, sitting, laying, etc.)
     * @param unit The room unit to cycle
     * @param type The type of room unit
     * @return true if the unit needs a status update
     */
    public boolean cycleRoomUnit(RoomUnit unit, RoomUnitType type) {
        boolean update = unit.needsStatusUpdate();

        if (unit.hasStatus(RoomUnitStatus.SIGN)) {
            this.room.sendComposer(new RoomUserStatusComposer(unit).compose());
            unit.removeStatus(RoomUnitStatus.SIGN);
        }

        if (unit.isWalking() && unit.getPath() != null && !unit.getPath().isEmpty()) {
            if (!unit.cycle(this.room)) {
                return true;
            }
        } else {
            if (unit.hasStatus(RoomUnitStatus.MOVE) && !unit.animateWalk) {
                unit.removeStatus(RoomUnitStatus.MOVE);
                update = true;
            }

            if (!unit.isWalking() && !unit.cmdSit) {
                // Don't override special pet statuses with SIT
                boolean hasSpecialPetStatus = unit.hasStatus(RoomUnitStatus.HANG)
                        || unit.hasStatus(RoomUnitStatus.SWING)
                        || unit.hasStatus(RoomUnitStatus.FLAME)
                        || unit.hasStatus(RoomUnitStatus.PLAY);

                RoomTile thisTile = this.room.getLayout().getTile(unit.getX(), unit.getY());
                HabboItem topItem = this.room.getTallestChair(thisTile);

                if (topItem == null || !topItem.getBaseItem().allowSit()) {
                    if (unit.hasStatus(RoomUnitStatus.SIT)) {
                        unit.removeStatus(RoomUnitStatus.SIT);
                        update = true;
                    }
                } else if (!hasSpecialPetStatus && thisTile.state == RoomTileState.SIT && (!unit.hasStatus(RoomUnitStatus.SIT)
                        || unit.sitUpdate)) {
                    this.room.dance(unit, DanceType.NONE);
                    unit.setStatus(RoomUnitStatus.SIT, (Item.getCurrentHeight(topItem) * 1.0D) + "");
                    unit.setZ(topItem.getZ());
                    unit.setRotation(RoomUserRotation.values()[topItem.getRotation()]);
                    unit.sitUpdate = false;
                    return true;
                }
            }
        }

        if (!unit.isWalking() && !unit.cmdLay) {
            HabboItem topItem = this.room.getTopItemAt(unit.getX(), unit.getY());

            if (topItem == null || !topItem.getBaseItem().allowLay()) {
                if (unit.hasStatus(RoomUnitStatus.LAY)) {
                    unit.removeStatus(RoomUnitStatus.LAY);
                    update = true;
                }
            } else {
                if (!unit.hasStatus(RoomUnitStatus.LAY)) {
                    BedProfile bedProfile = new BedProfile(topItem);
                    double layHeight = Item.getCurrentHeight(topItem) * 1.0D + bedProfile.getLayZOffset();
                    LOGGER.debug("[BedProfile] item={} stackHeight={} isFlat={} isDouble={} X={} Y={} Z={}",
                            topItem.getBaseItem().getName(), topItem.getBaseItem().getHeight(),
                            bedProfile.isFlat(), bedProfile.isDouble(),
                            bedProfile.getLayXOffset(), bedProfile.getLayYOffset(), bedProfile.getLayZOffset());
                    unit.setStatus(RoomUnitStatus.LAY, layHeight + ";" + bedProfile.getLayXOffset() + ";" + bedProfile.getLayYOffset());
                    unit.setRotation(RoomUserRotation.values()[topItem.getRotation() % 4]);
                    unit.setLocation(bedProfile.snapToLay(this.room, topItem, unit.getX(), unit.getY()));

                    // Check love effect when a user enters a bed
                    this.room.getUnitManager().checkBedLoveEffect(topItem);

                    update = true;
                }
            }
        }

        if (update) {
            unit.statusUpdate(false);
        }

        return update;
    }
}
