package polaris.hotel.rooms;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.achievements.AchievementManager;
import polaris.hotel.bots.Bot;
import polaris.hotel.gameclients.GameClientFlushBatch;
import polaris.hotel.items.ICycleable;
import polaris.hotel.pets.Pet;
import polaris.hotel.users.DanceType;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.core.WiredDisablerPauses;
import polaris.hotel.wired.core.WiredMoveCarryHelper;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.rooms.RoomAccessDeniedComposer;
import polaris.messages.outgoing.rooms.items.ItemsDataUpdateComposer;
import polaris.messages.outgoing.rooms.users.RoomUserStatusComposer;
import polaris.plugin.events.users.UserExitRoomEvent;

public class RoomCycleManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomCycleManager.class);

    private final Room room;
    private final RoomPostureService posture;
    private boolean cycleOdd;
    private long cycleTimestamp;
    private int idleHostingCycles;
    private long rollerCycle = System.currentTimeMillis();

    public RoomCycleManager(Room room) {
        this.room = room;
        this.posture = new RoomPostureService(room);
        this.cycleOdd = false;
        this.cycleTimestamp = 0;
        this.idleHostingCycles = 0;
    }

    public long getCycleTimestamp() {
        return this.cycleTimestamp;
    }

    public void resetIdleCycles() {
        this.room.resetIdleCycles();
    }

    public void cycle() {
        try (GameClientFlushBatch ignored = GameClientFlushBatch.open()) {
            this.cycleWithCoalescedFlushes();
        }
    }

    private void cycleWithCoalescedFlushes() {
        this.cycleOdd = !this.cycleOdd;
        this.cycleTimestamp = System.currentTimeMillis();
        WiredDisablerPauses.onRoomTick(this.room.getId(), RoomWiredDisableSupport.isWiredDisabled(this.room));
        final boolean[] foundRightHolder = {false};

        boolean loaded = this.room.isLoaded();

        if (loaded) {
            // Wired box flashes go out first, ahead of the movement packets of the same cycle.
            processWiredBoxFlashes();
            processScheduledTasks();
            processCycleTasks();
            processDecoHosting();

            if (!this.room.getCurrentUsers().isEmpty()) {
                this.advanceIdleUnload(false);

                Set<RoomUnit> updatedUnit = new HashSet<>();
                ArrayList<User> toKick = new ArrayList<>();

                final long millis = System.currentTimeMillis();

                for (User user : this.room.getCurrentUsers().values()) {
                    if (!foundRightHolder[0]) {
                        foundRightHolder[0] = user.getRoomUnit().getRightsLevel() != RoomRightLevels.NONE;
                    }

                    processUserHandItem(user, millis);
                    processUserEffect(user, millis);
                    processUserKick(user);
                    processUserIdle(user, toKick);
                    processUserMute(user);
                    processUserChatCounter(user);

                    if (this.cycleRoomUnit(user.getRoomUnit(), RoomUnitType.USER, user)) {
                        updatedUnit.add(user.getRoomUnit());
                    }
                }

                for (User user : toKick) {
                    this.room.gameEnvironment().getRoomManager().leaveRoom(user, this.room);
                }

                processBots(updatedUnit);
                processPets(updatedUnit);
                processRollers(updatedUnit);

                if (!updatedUnit.isEmpty()) {
                    ServerMessage statusComposer = new RoomUserStatusComposer(updatedUnit, true).compose();
                    WiredMoveCarryHelper.beginMovementCollection();
                    ServerMessage wiredMovementsComposer;
                    try {
                        WiredMoveCarryHelper.processUserFollowers(this.room, updatedUnit);
                    } finally {
                        wiredMovementsComposer = WiredMoveCarryHelper.finishMovementCollection();
                    }

                    if (wiredMovementsComposer != null) {
                        ArrayList<ServerMessage> batchedMessages = new ArrayList<>(2);
                        batchedMessages.add(statusComposer);
                        batchedMessages.add(wiredMovementsComposer);
                        this.room.sendComposers(batchedMessages);
                    } else {
                        this.room.sendComposer(statusComposer);
                    }
                }

                if (this.room.getTraxManager() != null) {
                    this.room.getTraxManager().cycle();
                }
            } else if (this.advanceIdleUnload(true)) {
                this.room.dispose();
            }
        }

        processUserQueue(foundRightHolder[0]);
        processScheduledComposers();
    }

    boolean advanceIdleUnload(boolean empty) {
        return this.room.advanceIdleUnload(empty);
    }

    private void processWiredBoxFlashes() {
        Set<UserItem> flipped = this.room.getWiredRuntime().getBoxFlashes().drain();

        if (!flipped.isEmpty() && !this.room.isHideWired()) {
            this.room.sendComposer(new ItemsDataUpdateComposer(flipped).compose());
        }
    }

    private void processScheduledTasks() {
        Runnable task;
        while ((task = this.room.scheduledTasks.poll()) != null) {
            this.room.threading().run(task);
        }
    }

    private void processCycleTasks() {
        if (this.room.getRoomSpecialTypes() != null) {
            for (ICycleable task : this.room.getRoomSpecialTypes().getCycleTasks()) {
                task.cycle(this.room);
            }
        }
    }

    private void processDecoHosting() {
        if (Emulator.getConfig().getBoolean("hotel.rooms.deco_hosting")) {
            if (this.idleHostingCycles < 120) {
                this.idleHostingCycles++;
            } else {
                this.idleHostingCycles = 0;

                int amount = (int) this.room.getCurrentUsers().values().stream()
                        .filter(user -> user.getUserInfo().getId() != this.room.getOwnerId())
                        .count();
                if (amount > 0) {
                    AchievementManager.progressAchievement(
                            this.room.getOwnerId(),
                            this.room.gameEnvironment().getAchievementManager().getAchievement("RoomDecoHosting"),
                            amount);
                }
            }
        }
    }

    private void processUserHandItem(User user, long millis) {
        if (Room.HAND_ITEM_TIME > 0
                && user.getRoomUnit().getHandItem() > 0
                && millis - user.getRoomUnit().getHandItemTimestamp() > (Room.HAND_ITEM_TIME * 1000L)) {
            this.room.giveHandItem(user, 0);
        }
    }

    private void processUserEffect(User user, long millis) {
        if (user.getRoomUnit().getEffectId() > 0
                && millis / 1000 > user.getRoomUnit().getEffectEndTimestamp()) {
            this.room.giveEffect(user, 0, -1);
        }
    }

    private void processUserKick(User user) {
        if (user.getRoomUnit().isKicked) {
            user.getRoomUnit().kickCount++;

            if (user.getRoomUnit().kickCount >= 5) {
                final Room room = this.room;
                this.room.scheduledTasks.add(
                        () -> room.gameEnvironment().getRoomManager().leaveRoom(user, room));
            }
        }
    }

    private void processUserIdle(User user, ArrayList<User> toKick) {
        boolean roomIdleBehaviorEnabled = this.room.isIdleSleepEnabled() || this.room.isIdleAutokickEnabled();

        if (Emulator.getConfig().getBoolean("hotel.rooms.auto.idle") || roomIdleBehaviorEnabled) {
            int sleepCycles =
                    this.room.isIdleSleepEnabled() ? this.room.getIdleSleepTimeoutSeconds() * 2 : Room.IDLE_CYCLES;
            int kickCycles = this.room.isIdleAutokickEnabled()
                    ? this.room.getIdleAutokickTimeoutSeconds() * 2
                    : Room.IDLE_CYCLES_KICK;
            int previousIdleCycles = user.getRoomUnit().getIdleTimer();
            user.getRoomUnit().increaseIdleTimer();

            if (previousIdleCycles <= sleepCycles && user.getRoomUnit().getIdleTimer() > sleepCycles) {
                boolean danceIsNone = (user.getRoomUnit().getDanceType() == DanceType.NONE);
                if (danceIsNone) {
                    this.room.getUnitManager().idle(user);
                }
            }

            if (!this.room.isOwner(user) && user.getRoomUnit().getIdleTimer() >= kickCycles) {
                UserExitRoomEvent event = new UserExitRoomEvent(user, UserExitRoomEvent.UserExitRoomReason.KICKED_IDLE);
                this.room.pluginManager().fireEvent(event);

                if (!event.isCancelled()) {
                    toKick.add(user);
                }
            }
        }
    }

    private void processUserMute(User user) {
        if (user.getUserStats().mutedBubbleTracker && user.getUserStats().allowTalk()) {
            // The client counts the RemainingMutePeriod down itself; the room was never told of the
            // mute, so it is not told of its end either (an IgnoreResult 3 would unignore the user).
            user.getUserStats().mutedBubbleTracker = false;
        }
    }

    private void processUserChatCounter(User user) {
        if (this.cycleOdd && user.getUserStats().chatCounter.get() > 0) {
            user.getUserStats().chatCounter.decrementAndGet();
        }
    }

    private void processBots(Set<RoomUnit> updatedUnit) {
        Int2ObjectMap<Bot> currentBots = this.room.getCurrentBots();
        if (currentBots.isEmpty()) {
            return;
        }

        final ArrayList<Bot> bots;
        synchronized (currentBots) {
            bots = new ArrayList<>(currentBots.values());
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

                if (this.cycleRoomUnit(bot.getRoomUnit(), RoomUnitType.BOT, null)) {
                    updatedUnit.add(bot.getRoomUnit());
                }
            } catch (Exception e) {
                LOGGER.error("Caught exception", e);
            }
        }
    }

    private void processPets(Set<RoomUnit> updatedUnit) {
        Int2ObjectMap<Pet> currentPets = this.room.getCurrentPets();
        if (currentPets.isEmpty() || !this.room.isAllowBotsWalk()) {
            return;
        }

        final ArrayList<Pet> pets;
        synchronized (currentPets) {
            pets = new ArrayList<>(currentPets.values());
        }

        for (Pet pet : pets) {
            try {
                if (pet == null || pet.getRoomUnit() == null) {
                    continue;
                }

                if (this.cycleRoomUnit(pet.getRoomUnit(), RoomUnitType.PET, null)) {
                    updatedUnit.add(pet.getRoomUnit());
                }

                pet.cycle();

                if (pet.packetUpdate) {
                    updatedUnit.add(pet.getRoomUnit());
                    pet.packetUpdate = false;
                }

                if (pet.getRoomUnit().isWalking()
                        && pet.getRoomUnit().getPath().size() == 1
                        && pet.getRoomUnit().hasStatus(RoomUnitStatus.GESTURE)) {
                    pet.getRoomUnit().removeStatus(RoomUnitStatus.GESTURE);
                    updatedUnit.add(pet.getRoomUnit());
                }
            } catch (Exception e) {
                LOGGER.error("Caught exception", e);
            }
        }
    }

    private void processRollers(Set<RoomUnit> updatedUnit) {
        Integer transientRollerSpeed = this.room.getTransientRollerSpeedOverride();
        Integer controlledRollerSpeed = RoomQueueSpeedControlSupport.getEffectiveRollerSpeed(this.room);
        int rollerSpeed = transientRollerSpeed != null
                ? transientRollerSpeed
                : (controlledRollerSpeed != null) ? controlledRollerSpeed : this.room.getRollerSpeed();
        if (rollerSpeed != -1 && this.rollerCycle >= rollerSpeed) {
            this.rollerCycle = 0;
            this.room.getRollerManager().processRollerCycle(updatedUnit, this.cycleTimestamp);
        } else {
            this.rollerCycle++;
        }
    }

    private void processUserQueue(boolean foundRightHolder) {
        Int2ObjectMap<User> userQueue = this.room.getUserQueue();
        synchronized (userQueue) {
            if (!userQueue.isEmpty() && !foundRightHolder) {
                final Room room = this.room;
                for (User queuedUser : userQueue.values()) {
                    if (queuedUser.isOnline()) {
                        if (queuedUser.getUserInfo().getRoomQueueId() == room.getId()) {
                            queuedUser.getClient().sendResponse(new RoomAccessDeniedComposer(room.getId(), ""));
                        }
                    }
                }
                userQueue.clear();
            }
        }
    }

    private void processScheduledComposers() {
        if (!this.room.scheduledComposers.isEmpty()) {
            for (ServerMessage message : this.room.scheduledComposers) {
                this.room.sendComposer(message);
            }
            this.room.scheduledComposers.clear();
        }
    }

    /**
     * The horse of a user who is not walking stands under them, one unit lower; its status is sent when it had to
     * move or was still walking.
     */
    private void keepHorseUnderRider(RoomUnit unit, RoomUnit ridingUnit) {
        if (ridingUnit == null || unit.getCurrentLocation() == null) {
            return;
        }

        boolean horseMoving = ridingUnit.hasStatus(RoomUnitStatus.MOVE);
        RoomTile horseTile = ridingUnit.getCurrentLocation();
        boolean horseMisplaced = horseTile == null || horseTile.x != unit.getX() || horseTile.y != unit.getY();

        if (horseMoving || horseMisplaced) {
            ridingUnit.setPreviousLocation(horseTile != null ? horseTile : unit.getCurrentLocation());
            ridingUnit.setCurrentLocation(unit.getCurrentLocation());
            ridingUnit.setGoalLocation(unit.getCurrentLocation());
            ridingUnit.setZ(unit.getZ() - 1.0);
            ridingUnit.animateWalk = false;

            if (horseMoving) ridingUnit.removeStatus(RoomUnitStatus.MOVE);

            this.room.sendComposer(new RoomUserStatusComposer(ridingUnit).compose());
        }
    }

    public boolean cycleRoomUnit(RoomUnit unit, RoomUnitType type, User user) {
        boolean update = unit.needsStatusUpdate();
        boolean isRiding = type == RoomUnitType.USER
                && user != null
                && user.getUserInfo() != null
                && user.getUserInfo().getRiding() != null;

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

            if (isRiding) {
                this.keepHorseUnderRider(unit, user.getUserInfo().getRiding().getRoomUnit());
            }

            if (!unit.isWalking() && !unit.cmdSit) {
                RoomPostureService.ChairOutcome chair = this.posture.settleOnChair(unit, isRiding);
                if (chair == RoomPostureService.ChairOutcome.SAT) {
                    return true;
                }
                if (chair == RoomPostureService.ChairOutcome.STOOD_UP) {
                    update = true;
                }
            }
        }

        if (!unit.isWalking() && !unit.cmdLay && this.posture.settleOnBed(unit, isRiding)) {
            update = true;
        }

        if (update) {
            unit.statusUpdate(false);
        }

        return update;
    }
}
