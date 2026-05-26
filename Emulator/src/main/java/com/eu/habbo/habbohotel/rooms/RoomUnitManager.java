package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.bots.VisitorBot;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.pets.PetManager;
import com.eu.habbo.habbohotel.pets.PetVocalsType;
import com.eu.habbo.habbohotel.pets.RideablePet;
import com.eu.habbo.habbohotel.users.DanceType;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboGender;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredUserActionType;
import com.eu.habbo.habbohotel.wired.core.WiredFreezeUtil;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredMoveCarryHelper;
import com.eu.habbo.habbohotel.wired.core.WiredUserMovementHelper;
import com.eu.habbo.messages.outgoing.generic.alerts.GenericErrorMessagesComposer;
import com.eu.habbo.messages.outgoing.inventory.AddPetComposer;
import com.eu.habbo.messages.outgoing.rooms.pets.RoomPetComposer;
import com.eu.habbo.messages.outgoing.rooms.users.*;
import gnu.trove.TCollections;
import gnu.trove.iterator.TIntObjectIterator;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.procedure.TObjectProcedure;
import gnu.trove.set.hash.THashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages all room units (Habbos, Bots, Pets) within a room.
 * Handles adding, removing, and querying units, as well as effects and hand items.
 */
public class RoomUnitManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomUnitManager.class);
    static final int BED_LOVE_EFFECT_ID = 9;

    private final Room room;

    // Unit collections - these are the actual data stores
    private final ConcurrentHashMap<Integer, Habbo> currentHabbos = new ConcurrentHashMap<>(3);
    private final TIntObjectMap<Habbo> habboQueue = TCollections.synchronizedMap(new TIntObjectHashMap<>(0));
    private final TIntObjectMap<Bot> currentBots = TCollections.synchronizedMap(new TIntObjectHashMap<>(0));
    private final TIntObjectMap<Pet> currentPets = TCollections.synchronizedMap(new TIntObjectHashMap<>(0));

    // Unit counter for assigning IDs
    private volatile int unitCounter;

    public RoomUnitManager(Room room) {
        this.room = room;
    }

    // ==================== INITIALIZATION ====================

    /**
     * Clears all units and resets the counter.
     */
    public void clear() {
        synchronized (this.room.roomUnitLock) {
            for (Habbo habbo : this.currentHabbos.values()) {
                if (habbo.getRoomUnit() != null) {
                    WiredMoveCarryHelper.cleanupRoomUnit(habbo.getRoomUnit());
                    WiredUserMovementHelper.cleanupRoomUnit(habbo.getRoomUnit());
                }
            }
            for (Bot bot : this.currentBots.valueCollection()) {
                if (bot.getRoomUnit() != null) {
                    WiredMoveCarryHelper.cleanupRoomUnit(bot.getRoomUnit());
                    WiredUserMovementHelper.cleanupRoomUnit(bot.getRoomUnit());
                }
            }
            for (Pet pet : this.currentPets.valueCollection()) {
                if (pet.getRoomUnit() != null) {
                    WiredMoveCarryHelper.cleanupRoomUnit(pet.getRoomUnit());
                    WiredUserMovementHelper.cleanupRoomUnit(pet.getRoomUnit());
                }
            }
            this.unitCounter = 0;
            this.currentHabbos.clear();
            this.currentPets.clear();
            this.currentBots.clear();
        }
    }

    /**
     * Clears all bots from the room.
     */
    public void clearBots() {
        synchronized (this.room.roomUnitLock) {
            this.currentBots.clear();
        }
    }

    /**
     * Clears all pets from the room.
     */
    public void clearPets() {
        synchronized (this.room.roomUnitLock) {
            this.currentPets.clear();
        }
    }

    /**
     * Clears the habbo queue.
     */
    public void clearQueue() {
        synchronized (this.habboQueue) {
            this.habboQueue.clear();
        }
    }

    /**
     * Gets the current unit counter value.
     */
    public int getUnitCounter() {
        return this.unitCounter;
    }

    /**
     * Increments and returns the next unit ID.
     */
    public int getNextUnitId() {
        synchronized (this.room.roomUnitLock) {
            return this.unitCounter++;
        }
    }

    // ==================== HABBO MANAGEMENT ====================

    /**
     * Gets a Habbo by their user ID.
     */
    public Habbo getHabbo(int habboId) {
        return this.currentHabbos.get(habboId);
    }

    /**
     * Gets a Habbo by their username.
     */
    public Habbo getHabbo(String username) {
        for (Habbo habbo : this.currentHabbos.values()) {
            if (habbo.getHabboInfo().getUsername().equalsIgnoreCase(username)) {
                return habbo;
            }
        }
        return null;
    }

    /**
     * Gets a Habbo by their RoomUnit.
     */
    public Habbo getHabboByRoomUnit(RoomUnit roomUnit) {
        for (Habbo habbo : this.currentHabbos.values()) {
            if (habbo.getRoomUnit() == roomUnit) {
                return habbo;
            }
        }
        return null;
    }

    /**
     * Gets a Habbo by their RoomUnit ID.
     */
    public Habbo getHabboByRoomUnitId(int roomUnitId) {
        for (Habbo habbo : this.currentHabbos.values()) {
            if (habbo.getRoomUnit().getId() == roomUnitId) {
                return habbo;
            }
        }
        return null;
    }

    /**
     * Gets all Habbos in the room as a map.
     */
    public ConcurrentHashMap<Integer, Habbo> getCurrentHabbos() {
        return this.currentHabbos;
    }

    /**
     * Gets all Habbos in the room.
     */
    public Collection<Habbo> getHabbos() {
        return this.currentHabbos.values();
    }

    /**
     * Gets the number of Habbos in the room.
     */
    public int getHabboCount() {
        return this.currentHabbos.size();
    }

    /**
     * Checks if a Habbo is in the room.
     */
    public boolean hasHabbo(int habboId) {
        return this.currentHabbos.containsKey(habboId);
    }

    /**
     * Adds a Habbo to the room.
     */
    public void addHabbo(Habbo habbo) {
        synchronized (this.room.roomUnitLock) {
            habbo.getRoomUnit().setId(this.unitCounter);
            this.currentHabbos.put(habbo.getHabboInfo().getId(), habbo);
            this.unitCounter++;
            this.room.updateDatabaseUserCount();
        }
    }

    /**
     * Removes a Habbo from the room.
     */
    public void removeHabbo(Habbo habbo) {
        this.removeHabbo(habbo, false);
    }

    /**
     * Removes a Habbo from the room with option to send remove packet.
     */
    public void removeHabbo(Habbo habbo, boolean sendRemovePacket) {
        if (habbo == null) {
            return;
        }

        if (habbo.getRoomUnit() != null) {
            WiredMoveCarryHelper.cleanupRoomUnit(habbo.getRoomUnit());
            WiredUserMovementHelper.cleanupRoomUnit(habbo.getRoomUnit());
            WiredManager.triggerUserLeavesRoom(this.room, habbo.getRoomUnit());
            if (WiredFreezeUtil.isFrozen(habbo.getRoomUnit())) {
                WiredFreezeUtil.unfreeze(this.room, habbo.getRoomUnit());
            }
        }

        if (habbo.getRoomUnit() != null && habbo.getRoomUnit().getCurrentLocation() != null) {
            habbo.getRoomUnit().getCurrentLocation().removeUnit(habbo.getRoomUnit());
        }

          synchronized (this.room.roomUnitLock) {
              this.currentHabbos.remove(habbo.getHabboInfo().getId());
          }

          this.room.getUserVariableManager().clearAssignmentsForUser(habbo.getHabboInfo().getId());

          if (sendRemovePacket && habbo.getRoomUnit() != null && !habbo.getRoomUnit().isTeleporting) {
              this.room.sendComposer(new RoomUserRemoveComposer(habbo.getRoomUnit()).compose());
        }

        if (habbo.getRoomUnit().getCurrentLocation() != null) {
            HabboItem item = this.room.getTopItemAt(habbo.getRoomUnit().getX(), habbo.getRoomUnit().getY());

            if (item != null) {
                try {
                    item.onWalkOff(habbo.getRoomUnit(), this.room, new Object[]{});
                } catch (Exception e) {
                    LOGGER.error("Caught exception", e);
                }
            }
        }

        if (habbo.getHabboInfo().getCurrentGame() != null) {
            if (this.room.getGame(habbo.getHabboInfo().getCurrentGame()) != null) {
                this.room.getGame(habbo.getHabboInfo().getCurrentGame()).removeHabbo(habbo);
            }
        }

        RoomTrade trade = this.room.getActiveTradeForHabbo(habbo);

        if (trade != null) {
            trade.stopTrade(habbo);
        }

        if (habbo.getHabboInfo().getId() != this.room.getOwnerId()) {
            this.pickupPetsForHabbo(habbo);
        }

        this.room.updateDatabaseUserCount();
    }

    /**
     * Kicks a Habbo from the room.
     */
    public void kickHabbo(Habbo habbo, boolean alert) {
        if (alert) {
            habbo.getClient().sendResponse(
                    new GenericErrorMessagesComposer(GenericErrorMessagesComposer.KICKED_OUT_OF_THE_ROOM));
        }

        habbo.getRoomUnit().isKicked = true;
        habbo.getRoomUnit().setGoalLocation(this.room.getLayout().getDoorTile());

        if (habbo.getRoomUnit().getPath() == null || habbo.getRoomUnit().getPath().size() <= 1
                || this.room.isPublicRoom()) {
            habbo.getRoomUnit().setCanWalk(true);
            Emulator.getGameEnvironment().getRoomManager().leaveRoom(habbo, this.room);
        }
    }

    /**
     * Checks if there are Habbos at the specified position.
     */
    public boolean hasHabbosAt(int x, int y) {
        for (Habbo habbo : this.getHabbos()) {
            if (habbo.getRoomUnit().getX() == x && habbo.getRoomUnit().getY() == y) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets all Habbos at a specific position.
     */
    public THashSet<Habbo> getHabbosAt(short x, short y) {
        return this.getHabbosAt(this.room.getLayout().getTile(x, y));
    }

    /**
     * Gets all Habbos at a specific tile.
     */
    public THashSet<Habbo> getHabbosAt(RoomTile tile) {
        THashSet<Habbo> habbos = new THashSet<>();

        for (Habbo habbo : this.getHabbos()) {
            if (habbo.getRoomUnit().getCurrentLocation().equals(tile)) {
                habbos.add(habbo);
            }
        }

        return habbos;
    }

    /**
     * Gets all Habbos on a specific item.
     */
    public THashSet<Habbo> getHabbosOnItem(HabboItem item) {
        THashSet<Habbo> habbos = new THashSet<>();
        for (short x = item.getX(); x < item.getX() + item.getBaseItem().getLength(); x++) {
            for (short y = item.getY(); y < item.getY() + item.getBaseItem().getWidth(); y++) {
                habbos.addAll(this.getHabbosAt(x, y));
            }
        }

        return habbos;
    }

    /**
     * Updates all Habbos at a position.
     */
    public void updateHabbosAt(short x, short y) {
        this.updateHabbosAt(x, y, this.getHabbosAt(x, y));
    }

    /**
     * Updates specific Habbos at a position.
     */
    public void updateHabbosAt(short x, short y, THashSet<Habbo> habbos) {
        RoomTile tile = this.room.getLayout().getTile(x, y);

        if (tile == null) {
            return;
        }

        HabboItem topItem = this.room.getTopItemAt(x, y);

        for (Habbo habbo : habbos) {
            if (habbo.getRoomUnit() == null) {
                continue;
            }

            if (WiredMoveCarryHelper.shouldSuppressStatusUpdate(habbo.getRoomUnit())
                    || WiredUserMovementHelper.shouldSuppressStatusUpdate(habbo.getRoomUnit())) {
                continue;
            }

            double z = habbo.getRoomUnit().getCurrentLocation().getStackHeight();
            boolean hadLayStatus = habbo.getRoomUnit().hasStatus(RoomUnitStatus.LAY);

            if (habbo.getRoomUnit().hasStatus(RoomUnitStatus.SIT)
                    || (topItem != null && topItem.getBaseItem().allowSit())) {
                if (topItem != null && topItem.getBaseItem().allowSit()) {
                    if (!habbo.getRoomUnit().hasStatus(RoomUnitStatus.SIT)) {
                        this.dance(habbo, DanceType.NONE);
                    }
                    habbo.getRoomUnit().setZ(topItem.getZ());
                    habbo.getRoomUnit().setPreviousLocationZ(topItem.getZ());
                    habbo.getRoomUnit().setRotation(RoomUserRotation.fromValue(topItem.getRotation()));
                    habbo.getRoomUnit().setStatus(RoomUnitStatus.SIT,
                            String.valueOf(Item.getCurrentHeight(topItem)));
                    habbo.getRoomUnit().cmdSit = false;
                } else if (habbo.getRoomUnit().cmdSit) {
                    habbo.getRoomUnit().setZ(z - 0.5);
                    habbo.getRoomUnit().setPreviousLocationZ(z - 0.5);
                } else {
                    habbo.getRoomUnit().removeStatus(RoomUnitStatus.SIT);
                    habbo.getRoomUnit().setZ(z);
                    habbo.getRoomUnit().setPreviousLocationZ(z);
                }
            } else if (topItem != null && topItem.getBaseItem().allowLay()) {
                BedProfile bedProfile = new BedProfile(topItem);

                // Snap user to the correct pillow tile for the current bed rotation
                RoomTile pillowTile = bedProfile.snapToLay(this.room, topItem, habbo.getRoomUnit().getX(), habbo.getRoomUnit().getY());

                // For double beds: if another user already occupies this pillow, use the other side
                if (pillowTile != null && bedProfile.isDouble()) {
                    THashSet<Habbo> habbosAtPillow = this.getHabbosAt(pillowTile.x, pillowTile.y);
                    for (Habbo other : habbosAtPillow) {
                        if (other == habbo || other.getRoomUnit() == null) continue;
                        RoomTile otherSide = bedProfile.getOtherSide(this.room, topItem, pillowTile);
                        if (otherSide != null) {
                            pillowTile = otherSide;
                        }
                        break;
                    }
                }

                if (pillowTile != null) {
                    habbo.getRoomUnit().setLocation(pillowTile);
                }

                habbo.getRoomUnit().setZ(topItem.getZ());
                habbo.getRoomUnit().setPreviousLocationZ(topItem.getZ());
                habbo.getRoomUnit().setRotation(RoomUserRotation.fromValue(topItem.getRotation() % 4));
                double layHeight = Item.getCurrentHeight(topItem) + bedProfile.getLayZOffset();
                habbo.getRoomUnit().setStatus(RoomUnitStatus.LAY, layHeight + ";" + bedProfile.getLayXOffset() + ";" + bedProfile.getLayYOffset());
            } else {
                if (habbo.getRoomUnit().hasStatus(RoomUnitStatus.SIT)) {
                    habbo.getRoomUnit().removeStatus(RoomUnitStatus.SIT);
                }
                if (habbo.getRoomUnit().hasStatus(RoomUnitStatus.LAY)) {
                    habbo.getRoomUnit().removeStatus(RoomUnitStatus.LAY);
                }
                habbo.getRoomUnit().setZ(z);
                habbo.getRoomUnit().setPreviousLocationZ(z);
            }

            habbo.getRoomUnit().statusUpdate(true);

            if (!hadLayStatus && habbo.getRoomUnit().hasStatus(RoomUnitStatus.LAY)) {
                WiredManager.triggerUserPerformsAction(this.room, habbo.getRoomUnit(), WiredUserActionType.LAY, -1);
            }
        }

        if (!habbos.isEmpty()) {
            THashSet<RoomUnit> roomUnits = new THashSet<>();
            for (Habbo habbo : habbos) {
                if (habbo.getRoomUnit() == null
                        || WiredMoveCarryHelper.shouldSuppressStatusUpdate(habbo.getRoomUnit())
                        || WiredUserMovementHelper.shouldSuppressStatusUpdate(habbo.getRoomUnit())) {
                    continue;
                }
                roomUnits.add(habbo.getRoomUnit());
            }

            if (!roomUnits.isEmpty()) {
                this.room.sendComposer(new RoomUserStatusComposer(roomUnits, true).compose());
            }
        }

        if (topItem != null && topItem.getBaseItem().allowLay()) {
            this.checkBedLoveEffect(topItem);
        }
    }

    // ==================== HABBO QUEUE ====================

    /**
     * Adds a Habbo to the queue.
     */
    public void addToQueue(Habbo habbo) {
        synchronized (this.habboQueue) {
            this.habboQueue.put(habbo.getHabboInfo().getId(), habbo);
        }
    }

    /**
     * Removes a Habbo from the queue.
     */
    public Habbo removeFromQueue(int habboId) {
        synchronized (this.habboQueue) {
            return this.habboQueue.remove(habboId);
        }
    }

    /**
     * Checks if a Habbo is in the queue.
     */
    public boolean isInQueue(int habboId) {
        return this.habboQueue.containsKey(habboId);
    }

    /**
     * Gets the Habbo queue.
     */
    public TIntObjectMap<Habbo> getHabboQueue() {
        return this.habboQueue;
    }

    // ==================== BOT MANAGEMENT ====================

    /**
     * Loads bots from the database.
     */
    public void loadBots(Connection connection) {
        this.currentBots.clear();

        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT users.username AS owner_name, bots.* FROM bots INNER JOIN users ON bots.user_id = users.id WHERE room_id = ?")) {
            statement.setInt(1, this.room.getId());
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    Bot bot = Emulator.getGameEnvironment().getBotManager().loadBot(set);

                    if (bot != null) {
                        bot.setRoom(this.room);
                        bot.setRoomUnit(new RoomUnit());
                        bot.getRoomUnit().setRoomUnitType(RoomUnitType.BOT);
                        bot.getRoomUnit().setBodyRotation(RoomUserRotation.fromValue(set.getInt("rot")));
                        bot.getRoomUnit().setHeadRotation(RoomUserRotation.fromValue(set.getInt("rot")));
                        bot.getRoomUnit().setDanceType(DanceType.values()[set.getInt("dance")]);
                        bot.getRoomUnit().setLocation(this.room.getLayout().getTile(
                                (short) set.getInt("x"), (short) set.getInt("y")));
                        bot.getRoomUnit().setZ(set.getDouble("z"));
                        bot.getRoomUnit().setPreviousLocationZ(set.getDouble("z"));
                        bot.getRoomUnit().setPathFinderRoom(this.room);
                        bot.getRoomUnit().setCanWalk(set.getBoolean("freeroam"));
                        this.addBot(bot);

                        if (!this.room.getFurniOwnerNames().containsKey(bot.getOwnerId())) {
                            this.room.getFurniOwnerNames().put(bot.getOwnerId(), set.getString("owner_name"));
                        }
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    /**
     * Gets a Bot by ID.
     */
    public Bot getBot(int botId) {
        return this.currentBots.get(botId);
    }

    /**
     * Gets a Bot by RoomUnit.
     */
    public Bot getBot(RoomUnit roomUnit) {
        synchronized (this.currentBots) {
            TIntObjectIterator<Bot> iterator = this.currentBots.iterator();

            for (int i = this.currentBots.size(); i-- > 0; ) {
                try {
                    iterator.advance();
                } catch (NoSuchElementException e) {
                    LOGGER.error("Caught exception", e);
                    break;
                }

                if (iterator.value().getRoomUnit() == roomUnit) {
                    return iterator.value();
                }
            }
        }

        return null;
    }

    /**
     * Gets a Bot by RoomUnit ID.
     */
    public Bot getBotByRoomUnitId(int id) {
        synchronized (this.currentBots) {
            TIntObjectIterator<Bot> iterator = this.currentBots.iterator();

            for (int i = this.currentBots.size(); i-- > 0; ) {
                try {
                    iterator.advance();
                } catch (NoSuchElementException e) {
                    LOGGER.error("Caught exception", e);
                    break;
                }

                if (iterator.value().getRoomUnit().getId() == id) {
                    return iterator.value();
                }
            }
        }

        return null;
    }

    /**
     * Gets all Bots with a specific name.
     */
    public List<Bot> getBots(String name) {
        List<Bot> bots = new ArrayList<>();

        synchronized (this.currentBots) {
            TIntObjectIterator<Bot> iterator = this.currentBots.iterator();

            for (int i = this.currentBots.size(); i-- > 0; ) {
                try {
                    iterator.advance();
                } catch (NoSuchElementException e) {
                    LOGGER.error("Caught exception", e);
                    break;
                }

                if (iterator.value().getName().equalsIgnoreCase(name)) {
                    bots.add(iterator.value());
                }
            }
        }

        return bots;
    }

    /**
     * Gets all Bots in the room.
     */
    public Collection<Bot> getBots() {
        return this.currentBots.valueCollection();
    }

    /**
     * Gets the Bot map.
     */
    public TIntObjectMap<Bot> getCurrentBots() {
        return this.currentBots;
    }

    /**
     * Adds a Bot to the room.
     */
    public void addBot(Bot bot) {
        synchronized (this.room.roomUnitLock) {
            bot.getRoomUnit().setId(this.unitCounter);
            this.currentBots.put(bot.getId(), bot);
            this.unitCounter++;
        }
    }

    /**
     * Removes a Bot from the room.
     */
    public boolean removeBot(Bot bot) {
        synchronized (this.currentBots) {
            if (this.currentBots.containsKey(bot.getId())) {
                if (bot.getRoomUnit() != null) {
                    WiredMoveCarryHelper.cleanupRoomUnit(bot.getRoomUnit());
                    WiredUserMovementHelper.cleanupRoomUnit(bot.getRoomUnit());
                    if (bot.getRoomUnit().getCurrentLocation() != null) {
                        bot.getRoomUnit().getCurrentLocation().removeUnit(bot.getRoomUnit());
                    }
                }

                this.currentBots.remove(bot.getId());
                if (bot.getRoomUnit() != null) {
                    bot.getRoomUnit().setInRoom(false);
                }
                bot.setRoom(null);
                if (bot.getRoomUnit() != null) {
                    this.room.sendComposer(new RoomUserRemoveComposer(bot.getRoomUnit()).compose());
                }
                bot.setRoomUnit(null);
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if there are Bots at the specified position.
     */
    public boolean hasBotsAt(final int x, final int y) {
        final boolean[] result = {false};

        synchronized (this.currentBots) {
            this.currentBots.forEachValue(new TObjectProcedure<Bot>() {
                @Override
                public boolean execute(Bot object) {
                    if (object.getRoomUnit().getX() == x && object.getRoomUnit().getY() == y) {
                        result[0] = true;
                        return false;
                    }
                    return true;
                }
            });
        }

        return result[0];
    }

    /**
     * Gets all Bots at a specific tile.
     */
    public THashSet<Bot> getBotsAt(RoomTile tile) {
        THashSet<Bot> bots = new THashSet<>();
        synchronized (this.currentBots) {
            TIntObjectIterator<Bot> botIterator = this.currentBots.iterator();

            for (int i = this.currentBots.size(); i-- > 0; ) {
                try {
                    botIterator.advance();

                    if (botIterator.value().getRoomUnit().getCurrentLocation().equals(tile)) {
                        bots.add(botIterator.value());
                    }
                } catch (Exception e) {
                    break;
                }
            }
        }

        return bots;
    }

    /**
     * Gets all Bots on a specific item.
     */
    public THashSet<Bot> getBotsOnItem(HabboItem item) {
        THashSet<Bot> bots = new THashSet<>();
        for (short x = item.getX(); x < item.getX() + item.getBaseItem().getLength(); x++) {
            for (short y = item.getY(); y < item.getY() + item.getBaseItem().getWidth(); y++) {
                bots.addAll(this.getBotsAt(this.room.getLayout().getTile(x, y)));
            }
        }

        return bots;
    }

    /**
     * Updates all Bots at a position.
     */
    public void updateBotsAt(short x, short y) {
        RoomTile tile = this.room.getLayout().getTile(x, y);

        if (tile == null) {
            return;
        }

        THashSet<Bot> bots = this.getBotsAt(tile);
        HabboItem topItem = this.room.getTopItemAt(x, y);

        for (Bot bot : bots) {
            if (bot.getRoomUnit() == null) {
                continue;
            }

            double z = bot.getRoomUnit().getCurrentLocation().getStackHeight();

            if (topItem != null && topItem.getBaseItem().allowSit()) {
                bot.getRoomUnit().setZ(topItem.getZ());
                bot.getRoomUnit().setPreviousLocationZ(topItem.getZ());
                bot.getRoomUnit().setRotation(RoomUserRotation.fromValue(topItem.getRotation()));
                bot.getRoomUnit().setStatus(RoomUnitStatus.SIT,
                        String.valueOf(Item.getCurrentHeight(topItem)));
            } else if (topItem != null && topItem.getBaseItem().allowLay()) {
                bot.getRoomUnit().setZ(topItem.getZ());
                bot.getRoomUnit().setPreviousLocationZ(topItem.getZ());
                BedProfile botBedProfile = new BedProfile(topItem);
                double botLayHeight = Item.getCurrentHeight(topItem) + botBedProfile.getLayZOffset();
                bot.getRoomUnit().setStatus(RoomUnitStatus.LAY, botLayHeight + ";" + botBedProfile.getLayXOffset() + ";" + botBedProfile.getLayYOffset());
            } else {
                if (bot.getRoomUnit().hasStatus(RoomUnitStatus.SIT)) {
                    bot.getRoomUnit().removeStatus(RoomUnitStatus.SIT);
                }
                if (bot.getRoomUnit().hasStatus(RoomUnitStatus.LAY)) {
                    bot.getRoomUnit().removeStatus(RoomUnitStatus.LAY);
                }
                bot.getRoomUnit().setZ(z);
                bot.getRoomUnit().setPreviousLocationZ(z);
            }

            bot.getRoomUnit().statusUpdate(true);
        }

        if (!bots.isEmpty()) {
            this.room.sendComposer(new RoomUserStatusComposer(
                    bots.stream().map(Bot::getRoomUnit).collect(Collectors.toCollection(THashSet::new)),
                    true).compose());
        }
    }

    // ==================== PET MANAGEMENT ====================

    /**
     * Loads pets from the database.
     */
    public void loadPets(Connection connection) {
        this.currentPets.clear();

        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT users.username as pet_owner_name, users_pets.* FROM users_pets INNER JOIN users ON users_pets.user_id = users.id WHERE room_id = ?")) {
            statement.setInt(1, this.room.getId());
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    Pet pet = PetManager.loadPet(set);
                    pet.setRoom(this.room);
                    pet.setRoomUnit(new RoomUnit());
                    pet.getRoomUnit().setRoomUnitType(RoomUnitType.PET);
                    pet.getRoomUnit().setBodyRotation(RoomUserRotation.fromValue(set.getInt("rot")));
                    pet.getRoomUnit().setHeadRotation(RoomUserRotation.fromValue(set.getInt("rot")));
                    pet.getRoomUnit().setLocation(this.room.getLayout().getTile(
                            (short) set.getInt("x"), (short) set.getInt("y")));
                    pet.getRoomUnit().setZ(set.getDouble("z"));
                    pet.getRoomUnit().setPreviousLocationZ(set.getDouble("z"));
                    pet.getRoomUnit().setPathFinderRoom(this.room);
                    pet.getRoomUnit().setCanWalk(true);
                    this.addPet(pet);

                    if (!this.room.getFurniOwnerNames().containsKey(pet.getUserId())) {
                        this.room.getFurniOwnerNames().put(pet.getUserId(), set.getString("pet_owner_name"));
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    /**
     * Gets a Pet by ID.
     */
    public Pet getPet(int petId) {
        return this.currentPets.get(petId);
    }

    /**
     * Gets a Pet by RoomUnit.
     */
    public Pet getPet(RoomUnit roomUnit) {
        TIntObjectIterator<Pet> petIterator = this.currentPets.iterator();

        for (int i = this.currentPets.size(); i-- > 0; ) {
            try {
                petIterator.advance();
            } catch (NoSuchElementException e) {
                LOGGER.error("Caught exception", e);
                break;
            }

            if (petIterator.value().getRoomUnit() == roomUnit) {
                return petIterator.value();
            }
        }

        return null;
    }

    /**
     * Gets all Pets in the room.
     */
    public Collection<Pet> getPets() {
        return this.currentPets.valueCollection();
    }

    /**
     * Gets the Pet map.
     */
    public TIntObjectMap<Pet> getCurrentPets() {
        return this.currentPets;
    }

    /**
     * Adds a Pet to the room.
     */
    public void addPet(Pet pet) {
        synchronized (this.room.roomUnitLock) {
            pet.getRoomUnit().setId(this.unitCounter);
            this.currentPets.put(pet.getId(), pet);
            this.unitCounter++;

            Habbo habbo = this.getHabbo(pet.getUserId());
            if (habbo != null) {
                this.room.getFurniOwnerNames().put(pet.getUserId(),
                        this.getHabbo(pet.getUserId()).getHabboInfo().getUsername());
            }
        }
    }

    /**
     * Removes a Pet from the room.
     */
    public Pet removePet(int petId) {
        Pet pet = this.currentPets.remove(petId);
        if (pet != null && pet.getRoomUnit() != null) {
            WiredMoveCarryHelper.cleanupRoomUnit(pet.getRoomUnit());
            WiredUserMovementHelper.cleanupRoomUnit(pet.getRoomUnit());
        }
        return pet;
    }

    /**
     * Places a Pet in the room.
     */
    public void placePet(Pet pet, short x, short y, double z, int rot) {
        synchronized (this.currentPets) {
            RoomTile tile = this.room.getLayout().getTile(x, y);

            if (tile == null) {
                tile = this.room.getLayout().getDoorTile();
            }

            pet.setRoomUnit(new RoomUnit());
            pet.setRoom(this.room);
            pet.getRoomUnit().setGoalLocation(tile);
            pet.getRoomUnit().setLocation(tile);
            pet.getRoomUnit().setRoomUnitType(RoomUnitType.PET);
            pet.getRoomUnit().setCanWalk(true);
            pet.getRoomUnit().setPathFinderRoom(this.room);
            pet.getRoomUnit().setPreviousLocationZ(z);
            pet.getRoomUnit().setZ(z);
            if (pet.getRoomUnit().getCurrentLocation() == null) {
                pet.getRoomUnit().setLocation(this.room.getLayout().getDoorTile());
                pet.getRoomUnit().setRotation(RoomUserRotation.fromValue(
                        this.room.getLayout().getDoorDirection()));
            }

            pet.needsUpdate = true;

            Habbo owner = this.getHabbo(pet.getUserId());
            if (owner != null) {
                this.room.getFurniOwnerNames().put(pet.getUserId(),
                        owner.getHabboInfo().getUsername());
            }

            this.addPet(pet);
            this.room.sendComposer(new RoomPetComposer(pet).compose());
        }
    }

    /**
     * Checks if there are Pets at the specified position.
     */
    public boolean hasPetsAt(int x, int y) {
        synchronized (this.currentPets) {
            TIntObjectIterator<Pet> petIterator = this.currentPets.iterator();

            for (int i = this.currentPets.size(); i-- > 0; ) {
                try {
                    petIterator.advance();
                } catch (NoSuchElementException e) {
                    LOGGER.error("Caught exception", e);
                    break;
                }

                if (petIterator.value().getRoomUnit().getX() == x
                        && petIterator.value().getRoomUnit().getY() == y) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Gets all Pets at a specific tile.
     */
    public THashSet<Pet> getPetsAt(RoomTile tile) {
        THashSet<Pet> pets = new THashSet<>();
        synchronized (this.currentPets) {
            TIntObjectIterator<Pet> petIterator = this.currentPets.iterator();

            for (int i = this.currentPets.size(); i-- > 0; ) {
                try {
                    petIterator.advance();

                    if (petIterator.value().getRoomUnit().getCurrentLocation().equals(tile)) {
                        pets.add(petIterator.value());
                    }
                } catch (Exception e) {
                    break;
                }
            }
        }

        return pets;
    }

    /**
     * Updates all Pets at a position.
     */
    public void updatePetsAt(short x, short y) {
        RoomTile tile = this.room.getLayout().getTile(x, y);

        if (tile == null) {
            return;
        }

        THashSet<Pet> pets = this.getPetsAt(tile);
        HabboItem topItem = this.room.getTopItemAt(x, y);

        for (Pet pet : pets) {
            if (pet.getRoomUnit() == null) {
                continue;
            }

            double z = pet.getRoomUnit().getCurrentLocation().getStackHeight();

            if (topItem != null && topItem.getBaseItem().allowSit()) {
                pet.getRoomUnit().setZ(topItem.getZ());
                pet.getRoomUnit().setPreviousLocationZ(topItem.getZ());
            } else if (topItem != null && topItem.getBaseItem().allowLay()) {
                pet.getRoomUnit().setZ(topItem.getZ());
                pet.getRoomUnit().setPreviousLocationZ(topItem.getZ());
            } else {
                pet.getRoomUnit().setZ(z);
                pet.getRoomUnit().setPreviousLocationZ(z);
            }

            pet.getRoomUnit().statusUpdate(true);
        }

        if (!pets.isEmpty()) {
            this.room.sendComposer(new RoomUserStatusComposer(
                    pets.stream().map(Pet::getRoomUnit).collect(Collectors.toCollection(THashSet::new)),
                    true).compose());
        }
    }

    /**
     * Picks up all pets belonging to a Habbo.
     */
    public void pickupPetsForHabbo(Habbo habbo) {
        THashSet<Pet> pets = new THashSet<>();

        synchronized (this.currentPets) {
            TIntObjectIterator<Pet> petIterator = this.currentPets.iterator();

            for (int i = this.currentPets.size(); i-- > 0; ) {
                try {
                    petIterator.advance();
                } catch (NoSuchElementException e) {
                    LOGGER.error("Caught exception", e);
                    break;
                }

                if (petIterator.value().getUserId() == habbo.getHabboInfo().getId()) {
                    pets.add(petIterator.value());
                }
            }
        }

        for (Pet pet : pets) {
            pet.setRoom(null);
            pet.needsUpdate = true;

            if (pet instanceof RideablePet) {
                ((RideablePet) pet).setRider(null);
            }

            pet.run();  // Run synchronously to ensure DB is updated before returning pet to inventory
            habbo.getInventory().getPetsComponent().addPet(pet);
            habbo.getClient().sendResponse(new AddPetComposer(pet));
            this.currentPets.remove(pet.getId());
            this.room.sendComposer(new RoomUserRemoveComposer(pet.getRoomUnit()).compose());
        }
    }

    /**
     * Removes all pets from the room.
     */
    public void removeAllPets() {
        removeAllPets(-1);
    }

    /**
     * Removes all pets from the room, optionally keeping one Habbo's pets.
     * @param excludeUserId User ID whose pets should NOT be removed, -1 to remove all
     */
    public void removeAllPets(int excludeUserId) {
        THashSet<Pet> toRemove = new THashSet<>();

        synchronized (this.currentPets) {
            TIntObjectIterator<Pet> petIterator = this.currentPets.iterator();

            for (int i = this.currentPets.size(); i-- > 0; ) {
                try {
                    petIterator.advance();
                } catch (NoSuchElementException e) {
                    LOGGER.error("Caught exception", e);
                    break;
                }

                if (petIterator.value().getUserId() != excludeUserId) {
                    toRemove.add(petIterator.value());
                }
            }
        }

        for (Pet pet : toRemove) {
            pet.setRoom(null);
            pet.needsUpdate = true;

            if (pet instanceof RideablePet) {
                ((RideablePet) pet).setRider(null);
            }

            pet.run();  // Run synchronously to ensure DB is updated before room reload

            Habbo owner = Emulator.getGameEnvironment().getHabboManager().getHabbo(pet.getUserId());
            if (owner != null) {
                owner.getInventory().getPetsComponent().addPet(pet);
                owner.getClient().sendResponse(new AddPetComposer(pet));
            }

            this.currentPets.remove(pet.getId());
            this.room.sendComposer(new RoomUserRemoveComposer(pet.getRoomUnit()).compose());
        }
    }

    // ==================== COMBINED UNIT METHODS ====================

    /**
     * Gets all Habbos and Bots at a position.
     */
    public THashSet<RoomUnit> getHabbosAndBotsAt(short x, short y) {
        return this.getHabbosAndBotsAt(this.room.getLayout().getTile(x, y));
    }

    /**
     * Gets all Habbos and Bots at a tile.
     */
    public THashSet<RoomUnit> getHabbosAndBotsAt(RoomTile tile) {
        THashSet<RoomUnit> list = new THashSet<>();

        for (Bot bot : this.getBotsAt(tile)) {
            list.add(bot.getRoomUnit());
        }

        for (Habbo habbo : this.getHabbosAt(tile)) {
            list.add(habbo.getRoomUnit());
        }

        return list;
    }

    /**
     * Gets all room units (Habbos, Bots, Pets).
     */
    public THashSet<RoomUnit> getRoomUnits() {
        return getRoomUnits(null);
    }

    /**
     * Gets all room units at a specific tile.
     */
    public THashSet<RoomUnit> getRoomUnits(RoomTile atTile) {
        THashSet<RoomUnit> units = new THashSet<>();

        for (Habbo habbo : this.currentHabbos.values()) {
            if (habbo != null && habbo.getRoomUnit() != null && habbo.getRoomUnit().getRoom() != null
                    && habbo.getRoomUnit().getRoom().getId() == this.room.getId() && (atTile == null
                    || habbo.getRoomUnit().getCurrentLocation() == atTile)) {
                units.add(habbo.getRoomUnit());
            }
        }

        for (Pet pet : this.currentPets.valueCollection()) {
            if (pet != null && pet.getRoomUnit() != null && pet.getRoomUnit().getRoom() != null
                    && pet.getRoomUnit().getRoom().getId() == this.room.getId() && (atTile == null
                    || pet.getRoomUnit().getCurrentLocation() == atTile)) {
                units.add(pet.getRoomUnit());
            }
        }

        for (Bot bot : this.currentBots.valueCollection()) {
            if (bot != null && bot.getRoomUnit() != null && bot.getRoomUnit().getRoom() != null
                    && bot.getRoomUnit().getRoom().getId() == this.room.getId() && (atTile == null
                    || bot.getRoomUnit().getCurrentLocation() == atTile)) {
                units.add(bot.getRoomUnit());
            }
        }

        return units;
    }

    /**
     * Gets room units at a specific tile as a collection.
     */
    public Collection<RoomUnit> getRoomUnitsAt(RoomTile tile) {
        THashSet<RoomUnit> roomUnits = getRoomUnits();
        return roomUnits.stream().filter(unit -> unit.getCurrentLocation().equals(tile))
                .collect(Collectors.toSet());
    }

    // ==================== EFFECTS AND HAND ITEMS ====================

    /**
     * Gives an effect to a Habbo.
     */
    public void giveEffect(Habbo habbo, int effectId, int duration) {
        if (this.currentHabbos.containsKey(habbo.getHabboInfo().getId())) {
            this.giveEffect(habbo.getRoomUnit(), effectId, duration);
        }
    }

    /**
     * Gives an effect to a RoomUnit.
     */
    public void giveEffect(RoomUnit roomUnit, int effectId, int duration) {
        if (duration == -1 || duration == Integer.MAX_VALUE) {
            duration = Integer.MAX_VALUE;
        } else {
            duration += Emulator.getIntUnixTimestamp();
        }

        if (this.room.isAllowEffects() && roomUnit != null) {
            roomUnit.setEffectId(effectId, duration);
            this.room.sendComposer(new RoomUserEffectComposer(roomUnit).compose());
        }
    }

    public void checkBedLoveEffect(HabboItem bed) {
        if (bed == null || !bed.getBaseItem().allowLay()) return;

        BedProfile bedProfile = new BedProfile(bed);
        if (!bedProfile.isDouble()) return;

        THashSet<Habbo> habbosOnBed = this.getHabbosOnItem(bed);

        Habbo male = null;
        Habbo female = null;
        for (Habbo h : habbosOnBed) {
            if (h.getRoomUnit() == null || !h.getRoomUnit().hasStatus(RoomUnitStatus.LAY)) continue;
            if (h.getHabboInfo().getGender() == HabboGender.M && male == null) {
                male = h;
            } else if (h.getHabboInfo().getGender() == HabboGender.F && female == null) {
                female = h;
            }
        }

        if (male != null && female != null) {
            this.giveEffect(male.getRoomUnit(), BED_LOVE_EFFECT_ID, 5);
            this.giveEffect(female.getRoomUnit(), BED_LOVE_EFFECT_ID, 5);
        }
    }

    /**
     * Gives a hand item to a Habbo.
     */
    public void giveHandItem(Habbo habbo, int handItem) {
        this.giveHandItem(habbo.getRoomUnit(), handItem);
    }

    /**
     * Gives a hand item to a RoomUnit.
     */
    public void giveHandItem(RoomUnit roomUnit, int handItem) {
        roomUnit.setHandItem(handItem);
        this.room.sendComposer(new RoomUserHandItemComposer(roomUnit).compose());
    }

    // ==================== IDLE AND DANCE ====================

    /**
     * Sets a Habbo to idle state.
     */
    public void idle(Habbo habbo) {
        habbo.getRoomUnit().setIdle();

        if (habbo.getRoomUnit().getDanceType() != DanceType.NONE) {
            this.dance(habbo, DanceType.NONE);
        }

        this.room.sendComposer(new RoomUnitIdleComposer(habbo.getRoomUnit()).compose());
        WiredManager.triggerUserIdles(this.room, habbo.getRoomUnit());
    }

    /**
     * Removes idle state from a Habbo.
     */
    public void unIdle(Habbo habbo) {
        if (habbo == null || habbo.getRoomUnit() == null) {
            return;
        }

        boolean wasIdle = habbo.getRoomUnit().isIdle();
        habbo.getRoomUnit().resetIdleTimer();

        if (wasIdle) {
            this.room.sendComposer(new RoomUnitIdleComposer(habbo.getRoomUnit()).compose());
            WiredManager.triggerUserUnidles(this.room, habbo.getRoomUnit());
        }
    }

    /**
     * Makes a Habbo dance.
     */
    public void dance(Habbo habbo, DanceType danceType) {
        this.dance(habbo.getRoomUnit(), danceType);
    }

    /**
     * Makes a RoomUnit dance.
     */
    public void dance(RoomUnit unit, DanceType danceType) {
        if (unit.getDanceType() != danceType) {
            boolean isDancing = !unit.getDanceType().equals(DanceType.NONE);
            unit.setDanceType(danceType);
            this.room.sendComposer(new RoomUserDanceComposer(unit).compose());

            if (danceType.equals(DanceType.NONE) && isDancing) {
                WiredManager.triggerUserStopsDancing(this.room, unit);
            } else if (!danceType.equals(DanceType.NONE) && !isDancing) {
                WiredManager.triggerUserStartsDancing(this.room, unit);
            }
        }
    }

    // ==================== TELEPORTATION ====================

    /**
     * Teleports a Habbo to an item.
     */
    public void teleportHabboToItem(Habbo habbo, HabboItem item) {
        this.teleportRoomUnitToLocation(habbo.getRoomUnit(), item.getX(), item.getY(),
                item.getZ() + Item.getCurrentHeight(item));
    }

    /**
     * Teleports a Habbo to a location.
     */
    public void teleportHabboToLocation(Habbo habbo, short x, short y) {
        this.teleportRoomUnitToLocation(habbo.getRoomUnit(), x, y, 0.0);
    }

    /**
     * Teleports a RoomUnit to an item.
     */
    public void teleportRoomUnitToItem(RoomUnit roomUnit, HabboItem item) {
        this.teleportRoomUnitToLocation(roomUnit, item.getX(), item.getY(),
                item.getZ() + Item.getCurrentHeight(item));
    }

    /**
     * Teleports a RoomUnit to a location.
     */
    public void teleportRoomUnitToLocation(RoomUnit roomUnit, short x, short y) {
        this.teleportRoomUnitToLocation(roomUnit, x, y, 0.0);
    }

    /**
     * Teleports a RoomUnit to a location with specific height.
     */
    public void teleportRoomUnitToLocation(RoomUnit roomUnit, short x, short y, double z) {
        if (this.room.isLoaded()) {
            WiredFreezeUtil.onTeleport(this.room, roomUnit);

            RoomTile tile = this.room.getLayout().getTile(x, y);

            if (z < tile.z) {
                z = tile.z;
            }

            roomUnit.setLocation(tile);
            roomUnit.setGoalLocation(tile);
            roomUnit.setZ(z);
            roomUnit.setPreviousLocationZ(z);
            this.room.updateRoomUnit(roomUnit);
            WiredFreezeUtil.restoreWalkState(roomUnit);
        }
    }

    // ==================== VISITOR BOT HANDLING ====================

    /**
     * Handles Habbo entering the room (visitor bot notification and pet greeting).
     */
    public void habboEntered(Habbo habbo) {
        habbo.getRoomUnit().animateWalk = false;

        // Have pets greet their owner
        synchronized (this.currentPets) {
            TIntObjectIterator<Pet> petIterator = this.currentPets.iterator();
            for (int i = this.currentPets.size(); i-- > 0; ) {
                try {
                    petIterator.advance();
                    Pet pet = petIterator.value();
                    if (pet.getUserId() == habbo.getHabboInfo().getId()) {
                        // Pet sees its owner - greet them!
                        pet.say(pet.getPetData().randomVocal(PetVocalsType.GREET_OWNER));
                        pet.addHappiness(10);
                    }
                } catch (Exception e) {
                    break;
                }
            }
        }

        synchronized (this.currentBots) {
            if (habbo.getHabboInfo().getId() != this.room.getOwnerId()) {
                return;
            }

            TIntObjectIterator<Bot> botIterator = this.currentBots.iterator();

            for (int i = this.currentBots.size(); i-- > 0; ) {
                try {
                    botIterator.advance();

                    if (botIterator.value() instanceof VisitorBot) {
                        ((VisitorBot) botIterator.value()).onUserEnter(habbo);
                        break;
                    }
                } catch (Exception e) {
                    break;
                }
            }
        }

        HabboItem doorTileTopItem = this.room.getTopItemAt(habbo.getRoomUnit().getX(),
                habbo.getRoomUnit().getY());
        if (doorTileTopItem != null
                && !(doorTileTopItem instanceof com.eu.habbo.habbohotel.items.interactions.InteractionTeleportTile)) {
            try {
                doorTileTopItem.onWalkOn(habbo.getRoomUnit(), this.room, new Object[]{});
            } catch (Exception e) {
                LOGGER.error("Caught exception", e);
            }
        }
    }

    // ==================== SIT AND STAND ====================

    /**
     * Makes a Habbo sit.
     */
    public void makeSit(Habbo habbo) {
        if (habbo.getRoomUnit() == null) {
            return;
        }

        if (habbo.getRoomUnit().hasStatus(RoomUnitStatus.SIT) || !habbo.getRoomUnit().canForcePosture()) {
            return;
        }

        this.dance(habbo, DanceType.NONE);
        habbo.getRoomUnit().cmdSit = true;
        habbo.getRoomUnit().setBodyRotation(
                RoomUserRotation.values()[habbo.getRoomUnit().getBodyRotation().getValue()
                        - habbo.getRoomUnit().getBodyRotation().getValue() % 2]);
        habbo.getRoomUnit().setStatus(RoomUnitStatus.SIT, 0.5 + "");
        this.room.sendComposer(new RoomUserStatusComposer(habbo.getRoomUnit()).compose());
    }

    /**
     * Makes a Habbo stand.
     */
    public void makeStand(Habbo habbo) {
        if (habbo.getRoomUnit() == null) {
            return;
        }

        HabboItem item = this.room.getTopItemAt(habbo.getRoomUnit().getX(), habbo.getRoomUnit().getY());
        if (item == null || !item.getBaseItem().allowSit() || !item.getBaseItem().allowLay()) {
            habbo.getRoomUnit().cmdStand = true;
            habbo.getRoomUnit().setBodyRotation(
                    RoomUserRotation.values()[habbo.getRoomUnit().getBodyRotation().getValue()
                            - habbo.getRoomUnit().getBodyRotation().getValue() % 2]);
            habbo.getRoomUnit().removeStatus(RoomUnitStatus.SIT);
            this.room.sendComposer(new RoomUserStatusComposer(habbo.getRoomUnit()).compose());
        }
    }

    public void dispose() {
        for (Habbo habbo : this.currentHabbos.values()) {
            if (habbo.getRoomUnit() != null) {
                WiredMoveCarryHelper.cleanupRoomUnit(habbo.getRoomUnit());
                WiredUserMovementHelper.cleanupRoomUnit(habbo.getRoomUnit());
            }
        }
        for (Bot bot : this.currentBots.valueCollection()) {
            if (bot.getRoomUnit() != null) {
                WiredMoveCarryHelper.cleanupRoomUnit(bot.getRoomUnit());
                WiredUserMovementHelper.cleanupRoomUnit(bot.getRoomUnit());
            }
        }
        for (Pet pet : this.currentPets.valueCollection()) {
            if (pet.getRoomUnit() != null) {
                WiredMoveCarryHelper.cleanupRoomUnit(pet.getRoomUnit());
                WiredUserMovementHelper.cleanupRoomUnit(pet.getRoomUnit());
            }
        }
        this.currentHabbos.clear();
        this.currentBots.clear();
        this.currentPets.clear();
        this.habboQueue.clear();
    }
}
