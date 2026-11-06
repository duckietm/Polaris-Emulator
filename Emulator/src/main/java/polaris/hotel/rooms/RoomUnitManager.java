package polaris.hotel.rooms;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.hotel.bots.Bot;
import polaris.hotel.bots.VisitorBot;
import polaris.hotel.items.Item;
import polaris.hotel.pets.Pet;
import polaris.hotel.pets.PetManager;
import polaris.hotel.pets.PetVocalsType;
import polaris.hotel.pets.RideablePet;
import polaris.hotel.rooms.variables.RoomUserVariableManager;
import polaris.hotel.rooms.variables.UserVariableHolders;
import polaris.hotel.users.DanceType;
import polaris.hotel.users.User;
import polaris.hotel.users.UserGender;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.WiredUserActionType;
import polaris.hotel.wired.core.WiredFreezeUtil;
import polaris.hotel.wired.core.WiredManager;
import polaris.hotel.wired.core.WiredMoveCarryHelper;
import polaris.hotel.wired.core.WiredUserMovementHelper;
import polaris.messages.outgoing.generic.alerts.GenericErrorCode;
import polaris.messages.outgoing.generic.alerts.GenericErrorMessagesComposer;
import polaris.messages.outgoing.inventory.AddPetComposer;
import polaris.messages.outgoing.rooms.pets.RoomPetComposer;
import polaris.messages.outgoing.rooms.users.RoomUnitIdleComposer;
import polaris.messages.outgoing.rooms.users.RoomUserDanceComposer;
import polaris.messages.outgoing.rooms.users.RoomUserEffectComposer;
import polaris.messages.outgoing.rooms.users.RoomUserHandItemComposer;
import polaris.messages.outgoing.rooms.users.RoomUserRemoveComposer;
import polaris.messages.outgoing.rooms.users.RoomUserStatusComposer;

public class RoomUnitManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomUnitManager.class);
    static final int BED_LOVE_EFFECT_ID = 9;
    static final int RIDING_EFFECT_ID = 77;

    private final Room room;
    private final RoomUnitIndex index = new RoomUnitIndex();
    private final ConcurrentHashMap<Integer, User> currentUsers = index.users();
    private final Int2ObjectMap<User> userQueue = index.queue();
    private final Int2ObjectMap<Bot> currentBots = index.bots();
    private final Int2ObjectMap<Pet> currentPets = index.pets();

    public RoomUnitManager(Room room) {
        this.room = room;
    }

    public void clear() {
        synchronized (this.room.roomUnitLock) {
            for (User user : this.currentUsers.values()) {
                if (user.getRoomUnit() != null) {
                    WiredMoveCarryHelper.cleanupRoomUnit(user.getRoomUnit());
                    WiredUserMovementHelper.cleanupRoomUnit(user.getRoomUnit());
                }
            }
            for (Bot bot : this.currentBots.values()) {
                if (bot.getRoomUnit() != null) {
                    WiredMoveCarryHelper.cleanupRoomUnit(bot.getRoomUnit());
                    WiredUserMovementHelper.cleanupRoomUnit(bot.getRoomUnit());
                }
            }
            for (Pet pet : this.currentPets.values()) {
                if (pet.getRoomUnit() != null) {
                    WiredMoveCarryHelper.cleanupRoomUnit(pet.getRoomUnit());
                    WiredUserMovementHelper.cleanupRoomUnit(pet.getRoomUnit());
                }
            }
            this.index.resetUnitCounter();
            this.currentUsers.clear();
            this.currentPets.clear();
            this.currentBots.clear();
        }
    }

    public void clearBots() {
        synchronized (this.room.roomUnitLock) {
            this.currentBots.clear();
        }
    }

    public void clearPets() {
        synchronized (this.room.roomUnitLock) {
            this.currentPets.clear();
        }
    }

    public void clearQueue() {
        synchronized (this.userQueue) {
            this.userQueue.clear();
        }
    }

    public int getUnitCounter() {
        return this.index.unitCounter();
    }

    public int getNextUnitId() {
        synchronized (this.room.roomUnitLock) {
            return this.index.nextUnitId();
        }
    }

    public User getUser(int userId) {
        return this.currentUsers.get(userId);
    }

    public User getUser(String username) {
        for (User user : this.currentUsers.values()) {
            if (user.getUserInfo().getUsername().equalsIgnoreCase(username)) {
                return user;
            }
        }
        return null;
    }

    public User getUserByRoomUnit(RoomUnit roomUnit) {
        for (User user : this.currentUsers.values()) {
            if (user.getRoomUnit() == roomUnit) {
                return user;
            }
        }
        return null;
    }

    public User getUserByRoomUnitId(int roomUnitId) {
        for (User user : this.currentUsers.values()) {
            if (user.getRoomUnit().getId() == roomUnitId) {
                return user;
            }
        }
        return null;
    }

    public ConcurrentHashMap<Integer, User> getCurrentUsers() {
        return this.currentUsers;
    }

    public Collection<User> getUsers() {
        return this.currentUsers.values();
    }

    public int getUserCount() {
        return this.currentUsers.size();
    }

    public boolean hasUser(int userId) {
        return this.currentUsers.containsKey(userId);
    }

    public void addUser(User user) {
        synchronized (this.room.roomUnitLock) {
            user.getRoomUnit().setId(this.index.unitCounter());
            this.currentUsers.put(user.getUserInfo().getId(), user);
            this.index.incrementUnitId();
        }
        this.room.scheduleDatabaseUserCountUpdate();
    }

    public void removeUser(User user) {
        this.removeUser(user, false);
    }

    public void removeUser(User user, boolean sendRemovePacket) {
        if (user == null) {
            return;
        }

        if (user.getRoomUnit() != null) {
            WiredMoveCarryHelper.cleanupRoomUnit(user.getRoomUnit());
            WiredUserMovementHelper.cleanupRoomUnit(user.getRoomUnit());
            WiredManager.triggerUserLeavesRoom(this.room, user.getRoomUnit());
            if (WiredFreezeUtil.isFrozen(user.getRoomUnit())) {
                WiredFreezeUtil.unfreeze(this.room, user.getRoomUnit());
            }
        }

        if (user.getRoomUnit() != null && user.getRoomUnit().getCurrentLocation() != null) {
            user.getRoomUnit().getCurrentLocation().removeUnit(user.getRoomUnit());
        }

        synchronized (this.room.roomUnitLock) {
            this.currentUsers.remove(user.getUserInfo().getId());
        }
        this.room.forgetWiredOpacityUser(user.getUserInfo().getId());
        RoomWiredRuntime wiredRuntime = this.room.getWiredRuntime();
        if (wiredRuntime != null) {
            wiredRuntime.forgetVisualizationUser(
                    user.getUserInfo().getId(),
                    user.getRoomUnit() == null ? -1 : user.getRoomUnit().getId());
        }
        this.room
                .getUserVariableManager()
                .clearAssignmentsForUser(user.getUserInfo().getId());

        if (sendRemovePacket && user.getRoomUnit() != null && !user.getRoomUnit().isTeleporting) {
            this.room.sendComposer(new RoomUserRemoveComposer(user.getRoomUnit()).compose());
        }

        if (user.getRoomUnit().getCurrentLocation() != null) {
            UserItem item = this.room.getTopItemAt(
                    user.getRoomUnit().getX(), user.getRoomUnit().getY());

            if (item != null) {
                try {
                    item.onWalkOff(user.getRoomUnit(), this.room, new Object[] {});
                } catch (Exception e) {
                    LOGGER.error("Caught exception", e);
                }
            }
        }

        if (user.getUserInfo().getCurrentGame() != null) {
            if (this.room.getGame(user.getUserInfo().getCurrentGame()) != null) {
                this.room.getGame(user.getUserInfo().getCurrentGame()).removeUser(user);
            }
        }

        RoomTrade trade = this.room.getActiveTradeForUser(user);

        if (trade != null) {
            trade.stopTrade(user);
        }

        // So does a wired trade (a chest deposit or a contract): the offer goes back and the window closes.
        if (wiredRuntime != null) {
            wiredRuntime.getTradingManager().leftRoom(user);
        }

        if (user.getUserInfo().getId() != this.room.getOwnerId()) {
            this.pickupPetsForUser(user);
        }

        this.room.scheduleDatabaseUserCountUpdate();
    }

    public void kickUser(User user, boolean alert) {
        if (alert) {
            user.getClient().sendResponse(new GenericErrorMessagesComposer(GenericErrorCode.KICKED_OUT_OF_ROOM));
        }

        user.getRoomUnit().isKicked = true;
        user.getRoomUnit().setGoalLocation(this.room.getLayout().getDoorTile());

        if (user.getRoomUnit().getPath() == null
                || user.getRoomUnit().getPath().size() <= 1
                || this.room.isPublicRoom()) {
            user.getRoomUnit().setCanWalk(true);
            this.room.gameEnvironment().getRoomManager().leaveRoom(user, this.room);
        }
    }

    public boolean hasUsersAt(int x, int y) {
        for (User user : this.getUsers()) {
            if (user.getRoomUnit().getX() == x && user.getRoomUnit().getY() == y) {
                return true;
            }
        }
        return false;
    }

    public Set<User> getUsersAt(short x, short y) {
        return this.getUsersAt(this.room.getLayout().getTile(x, y));
    }

    public Set<User> getUsersAt(RoomTile tile) {
        Set<User> users = new HashSet<>();

        for (User user : this.getUsers()) {
            if (user.getRoomUnit().getCurrentLocation().equals(tile)) {
                users.add(user);
            }
        }

        return users;
    }

    public Set<User> getUsersOnItem(UserItem item) {
        Set<User> users = new HashSet<>();
        for (short x = item.getX(); x < item.getX() + item.getBaseItem().getLength(); x++) {
            for (short y = item.getY(); y < item.getY() + item.getBaseItem().getWidth(); y++) {
                users.addAll(this.getUsersAt(x, y));
            }
        }

        return users;
    }

    public void updateUsersAt(short x, short y) {
        this.updateUsersAt(x, y, this.getUsersAt(x, y));
    }

    public void updateUsersAt(short x, short y, Collection<User> users) {
        RoomTile tile = this.room.getLayout().getTile(x, y);

        if (tile == null) {
            return;
        }

        UserItem topItem = this.room.getTopItemAt(x, y);

        for (User user : users) {
            if (user.getRoomUnit() == null) {
                continue;
            }

            if (WiredMoveCarryHelper.shouldSuppressStatusUpdate(user.getRoomUnit())
                    || WiredUserMovementHelper.shouldSuppressStatusUpdate(user.getRoomUnit())) {
                continue;
            }

            double z = user.getRoomUnit().getCurrentLocation().getStackHeight();
            boolean hadLayStatus = user.getRoomUnit().hasStatus(RoomUnitStatus.LAY);
            boolean hadSitStatus = user.getRoomUnit().hasStatus(RoomUnitStatus.SIT);

            boolean isRiding = user.getUserInfo() != null && user.getUserInfo().getRiding() != null;

            if (isRiding) {
                if (user.getRoomUnit().hasStatus(RoomUnitStatus.SIT)) {
                    user.getRoomUnit().removeStatus(RoomUnitStatus.SIT);
                }
                if (user.getRoomUnit().hasStatus(RoomUnitStatus.LAY)) {
                    user.getRoomUnit().removeStatus(RoomUnitStatus.LAY);
                }
            } else if (user.getRoomUnit().hasStatus(RoomUnitStatus.SIT)
                    || (topItem != null && topItem.getBaseItem().allowSit())) {
                if (topItem != null && topItem.getBaseItem().allowSit()) {
                    if (!user.getRoomUnit().hasStatus(RoomUnitStatus.SIT)) {
                        this.dance(user, DanceType.NONE);
                    }
                    user.getRoomUnit().setZ(topItem.getZ());
                    user.getRoomUnit().setPreviousLocationZ(topItem.getZ());
                    user.getRoomUnit().setRotation(RoomUserRotation.fromValue(topItem.getRotation()));
                    user.getRoomUnit().setStatus(RoomUnitStatus.SIT, String.valueOf(Item.getCurrentHeight(topItem)));
                    user.getRoomUnit().cmdSit = false;
                } else if (user.getRoomUnit().cmdSit) {
                    user.getRoomUnit().setZ(z - 0.5);
                    user.getRoomUnit().setPreviousLocationZ(z - 0.5);
                } else {
                    user.getRoomUnit().removeStatus(RoomUnitStatus.SIT);
                    user.getRoomUnit().setZ(z);
                    user.getRoomUnit().setPreviousLocationZ(z);
                }
            } else if (topItem != null && topItem.getBaseItem().allowLay()) {
                BedProfile.lay(this.room, topItem, user.getRoomUnit());
            } else {
                if (user.getRoomUnit().hasStatus(RoomUnitStatus.SIT)) {
                    user.getRoomUnit().removeStatus(RoomUnitStatus.SIT);
                }
                if (user.getRoomUnit().hasStatus(RoomUnitStatus.LAY)) {
                    user.getRoomUnit().removeStatus(RoomUnitStatus.LAY);
                }
                user.getRoomUnit().setZ(z);
                user.getRoomUnit().setPreviousLocationZ(z);
            }

            user.getRoomUnit().statusUpdate(true);

            if (!hadLayStatus && user.getRoomUnit().hasStatus(RoomUnitStatus.LAY)) {
                WiredManager.triggerUserPerformsAction(this.room, user.getRoomUnit(), WiredUserActionType.LAY, -1);
            }

            if (!hadSitStatus && user.getRoomUnit().hasStatus(RoomUnitStatus.SIT)) {
                WiredManager.triggerUserPerformsAction(this.room, user.getRoomUnit(), WiredUserActionType.SIT, -1);
            }
        }

        if (!users.isEmpty()) {
            Set<RoomUnit> roomUnits = new HashSet<>();
            for (User user : users) {
                if (user.getRoomUnit() == null
                        || WiredMoveCarryHelper.shouldSuppressStatusUpdate(user.getRoomUnit())
                        || WiredUserMovementHelper.shouldSuppressStatusUpdate(user.getRoomUnit())) {
                    continue;
                }
                roomUnits.add(user.getRoomUnit());
            }

            if (!roomUnits.isEmpty()) {
                this.room.sendComposer(new RoomUserStatusComposer(roomUnits, true).compose());
            }
        }

        if (topItem != null && topItem.getBaseItem().allowLay()) {
            this.checkBedLoveEffect(topItem);
        }
    }

    public void addToQueue(User user) {
        synchronized (this.userQueue) {
            this.userQueue.put(user.getUserInfo().getId(), user);
        }
    }

    public User removeFromQueue(int userId) {
        synchronized (this.userQueue) {
            return this.userQueue.remove(userId);
        }
    }

    public boolean isInQueue(int userId) {
        return this.userQueue.containsKey(userId);
    }

    public Int2ObjectMap<User> getUserQueue() {
        return this.userQueue;
    }

    public void loadBots(Connection connection) {
        this.currentBots.clear();

        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT users.username AS owner_name, bots.* FROM bots INNER JOIN users ON bots.user_id = users.id WHERE room_id = ?")) {
            statement.setInt(1, this.room.getId());
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    Bot bot = this.room.gameEnvironment().getBotManager().loadBot(set);

                    if (bot != null) {
                        bot.setRoom(this.room);
                        bot.setRoomUnit(new RoomUnit());
                        bot.getRoomUnit().setRoomUnitType(RoomUnitType.BOT);
                        bot.getRoomUnit().setBodyRotation(RoomUserRotation.fromValue(set.getInt("rot")));
                        bot.getRoomUnit().setHeadRotation(RoomUserRotation.fromValue(set.getInt("rot")));
                        bot.getRoomUnit().setDanceType(DanceType.values()[set.getInt("dance")]);
                        bot.getRoomUnit().setLocation(this.room.getLayout().getTile((short) set.getInt("x"), (short)
                                set.getInt("y")));
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

    public Bot getBot(int botId) {
        return this.currentBots.get(botId);
    }

    public Bot getBot(RoomUnit roomUnit) {
        synchronized (this.currentBots) {
            for (Bot bot : this.currentBots.values()) {
                if (bot.getRoomUnit() == roomUnit) {
                    return bot;
                }
            }
        }

        return null;
    }

    public Bot getBotByRoomUnitId(int id) {
        synchronized (this.currentBots) {
            for (Bot bot : this.currentBots.values()) {
                if (bot.getRoomUnit().getId() == id) {
                    return bot;
                }
            }
        }

        return null;
    }

    public List<Bot> getBots(String name) {
        List<Bot> bots = new ArrayList<>();

        synchronized (this.currentBots) {
            for (Bot bot : this.currentBots.values()) {
                if (bot.getName().equalsIgnoreCase(name)) {
                    bots.add(bot);
                }
            }
        }

        return bots;
    }

    public Collection<Bot> getBots() {
        return this.currentBots.values();
    }

    public Int2ObjectMap<Bot> getCurrentBots() {
        return this.currentBots;
    }

    public void addBot(Bot bot) {
        synchronized (this.room.roomUnitLock) {
            bot.getRoomUnit().setId(this.index.unitCounter());
            this.currentBots.put(bot.getId(), bot);
            this.index.incrementUnitId();
        }
        this.restoreUnitVariables(UserVariableHolders.ofBot(bot.getId()));
    }

    /** A pet or bot placed in a loaded room gets its permanent user variables back. */
    private void restoreUnitVariables(int holderKey) {
        RoomUserVariableManager variables = this.room.isLoaded() ? this.room.getUserVariableManager() : null;
        if (variables != null) {
            variables.restorePermanentAssignments(holderKey);
        }
    }

    private void forgetUnitVariables(int holderKey) {
        RoomUserVariableManager variables = this.room.getUserVariableManager();
        if (variables != null) {
            variables.clearAssignmentsForUser(holderKey);
        }
    }

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
                this.forgetUnitVariables(UserVariableHolders.ofBot(bot.getId()));
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

    public boolean hasBotsAt(final int x, final int y) {
        synchronized (this.currentBots) {
            for (Bot bot : this.currentBots.values()) {
                if (bot.getRoomUnit().getX() == x && bot.getRoomUnit().getY() == y) {
                    return true;
                }
            }
        }

        return false;
    }

    public Set<Bot> getBotsAt(RoomTile tile) {
        Set<Bot> bots = new HashSet<>();
        synchronized (this.currentBots) {
            for (Bot bot : this.currentBots.values()) {
                if (bot.getRoomUnit().getCurrentLocation().equals(tile)) {
                    bots.add(bot);
                }
            }
        }

        return bots;
    }

    public Set<Bot> getBotsOnItem(UserItem item) {
        Set<Bot> bots = new HashSet<>();
        for (short x = item.getX(); x < item.getX() + item.getBaseItem().getLength(); x++) {
            for (short y = item.getY(); y < item.getY() + item.getBaseItem().getWidth(); y++) {
                bots.addAll(this.getBotsAt(this.room.getLayout().getTile(x, y)));
            }
        }

        return bots;
    }

    public void updateBotsAt(short x, short y) {
        RoomTile tile = this.room.getLayout().getTile(x, y);

        if (tile == null) {
            return;
        }

        Set<Bot> bots = this.getBotsAt(tile);
        UserItem topItem = this.room.getTopItemAt(x, y);

        for (Bot bot : bots) {
            // A bot a wired move carries gets its height from the carry, as an avatar does.
            if (bot.getRoomUnit() == null
                    || WiredMoveCarryHelper.shouldSuppressStatusUpdate(bot.getRoomUnit())
                    || WiredUserMovementHelper.shouldSuppressStatusUpdate(bot.getRoomUnit())) {
                continue;
            }

            double z = bot.getRoomUnit().getCurrentLocation().getStackHeight();

            if (topItem != null && topItem.getBaseItem().allowSit()) {
                bot.getRoomUnit().setZ(topItem.getZ());
                bot.getRoomUnit().setPreviousLocationZ(topItem.getZ());
                bot.getRoomUnit().setRotation(RoomUserRotation.fromValue(topItem.getRotation()));
                bot.getRoomUnit().setStatus(RoomUnitStatus.SIT, String.valueOf(Item.getCurrentHeight(topItem)));
            } else if (topItem != null && topItem.getBaseItem().allowLay()) {
                BedProfile.layInPlace(topItem, bot.getRoomUnit());
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
                            bots.stream().map(Bot::getRoomUnit).collect(Collectors.toCollection(HashSet::new)), true)
                    .compose());
        }
    }

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
                    pet.getRoomUnit().setLocation(this.room.getLayout().getTile((short) set.getInt("x"), (short)
                            set.getInt("y")));
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

    public Pet getPet(int petId) {
        return this.currentPets.get(petId);
    }

    public Pet getPet(RoomUnit roomUnit) {
        synchronized (this.currentPets) {
            for (Pet pet : this.currentPets.values()) {
                if (pet.getRoomUnit() == roomUnit) {
                    return pet;
                }
            }
        }

        return null;
    }

    public Collection<Pet> getPets() {
        return this.currentPets.values();
    }

    public Int2ObjectMap<Pet> getCurrentPets() {
        return this.currentPets;
    }

    public void addPet(Pet pet) {
        synchronized (this.room.roomUnitLock) {
            pet.getRoomUnit().setId(this.index.unitCounter());
            this.currentPets.put(pet.getId(), pet);
            this.index.incrementUnitId();

            User user = this.getUser(pet.getUserId());
            if (user != null) {
                this.room
                        .getFurniOwnerNames()
                        .put(
                                pet.getUserId(),
                                this.getUser(pet.getUserId()).getUserInfo().getUsername());
            }
        }
        this.restoreUnitVariables(UserVariableHolders.ofPet(pet.getId()));
    }

    public Pet removePet(int petId) {
        Pet pet = this.currentPets.remove(petId);
        if (pet != null) {
            this.forgetUnitVariables(UserVariableHolders.ofPet(pet.getId()));
        }
        if (pet != null && pet.getRoomUnit() != null) {
            WiredMoveCarryHelper.cleanupRoomUnit(pet.getRoomUnit());
            WiredUserMovementHelper.cleanupRoomUnit(pet.getRoomUnit());
        }
        return pet;
    }

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
                pet.getRoomUnit()
                        .setRotation(
                                RoomUserRotation.fromValue(this.room.getLayout().getDoorDirection()));
            }

            pet.needsUpdate = true;

            User owner = this.getUser(pet.getUserId());
            if (owner != null) {
                this.room
                        .getFurniOwnerNames()
                        .put(pet.getUserId(), owner.getUserInfo().getUsername());
            }

            this.addPet(pet);
            this.room.sendComposer(new RoomPetComposer(pet).compose());
        }
    }

    public boolean hasPetsAt(int x, int y) {
        synchronized (this.currentPets) {
            for (Pet pet : this.currentPets.values()) {
                if (pet.getRoomUnit().getX() == x && pet.getRoomUnit().getY() == y) {
                    return true;
                }
            }
        }

        return false;
    }

    public Set<Pet> getPetsAt(RoomTile tile) {
        Set<Pet> pets = new HashSet<>();
        synchronized (this.currentPets) {
            for (Pet pet : this.currentPets.values()) {
                if (pet.getRoomUnit().getCurrentLocation().equals(tile)) {
                    pets.add(pet);
                }
            }
        }

        return pets;
    }

    public void updatePetsAt(short x, short y) {
        RoomTile tile = this.room.getLayout().getTile(x, y);

        if (tile == null) {
            return;
        }

        Set<Pet> pets = this.getPetsAt(tile);
        UserItem topItem = this.room.getTopItemAt(x, y);

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
                            pets.stream().map(Pet::getRoomUnit).collect(Collectors.toCollection(HashSet::new)), true)
                    .compose());
        }
    }

    public void pickupPetsForUser(User user) {
        Set<Pet> pets = new HashSet<>();

        synchronized (this.currentPets) {
            for (Pet pet : this.currentPets.values()) {
                if (pet.getUserId() == user.getUserInfo().getId()) {
                    pets.add(pet);
                }
            }
        }

        for (Pet pet : pets) {
            pet.setRoom(null);
            pet.needsUpdate = true;

            if (pet instanceof RideablePet) {
                ((RideablePet) pet).setRider(null);
            }

            pet.run(); // Run synchronously to ensure DB is updated before returning pet to inventory
            user.getInventory().getPetsComponent().addPet(pet);
            user.getClient().sendResponse(new AddPetComposer(pet));
            this.currentPets.remove(pet.getId());
            this.room.sendComposer(new RoomUserRemoveComposer(pet.getRoomUnit()).compose());
        }
    }

    public void removeAllPets() {
        removeAllPets(-1);
    }

    public void removeAllPets(int excludeUserId) {
        Set<Pet> toRemove = new HashSet<>();

        synchronized (this.currentPets) {
            for (Pet pet : this.currentPets.values()) {
                if (pet.getUserId() != excludeUserId) {
                    toRemove.add(pet);
                }
            }
        }

        for (Pet pet : toRemove) {
            pet.setRoom(null);
            pet.needsUpdate = true;

            if (pet instanceof RideablePet) {
                ((RideablePet) pet).setRider(null);
            }

            pet.run(); // Run synchronously to ensure DB is updated before room reload

            User owner = this.room.gameEnvironment().getUserManager().getUser(pet.getUserId());
            if (owner != null) {
                owner.getInventory().getPetsComponent().addPet(pet);
                owner.getClient().sendResponse(new AddPetComposer(pet));
            }

            this.currentPets.remove(pet.getId());
            this.room.sendComposer(new RoomUserRemoveComposer(pet.getRoomUnit()).compose());
        }
    }

    public Set<RoomUnit> getUsersAndBotsAt(short x, short y) {
        return this.getUsersAndBotsAt(this.room.getLayout().getTile(x, y));
    }

    public Set<RoomUnit> getUsersAndBotsAt(RoomTile tile) {
        Set<RoomUnit> list = new HashSet<>();

        for (Bot bot : this.getBotsAt(tile)) {
            list.add(bot.getRoomUnit());
        }

        for (User user : this.getUsersAt(tile)) {
            list.add(user.getRoomUnit());
        }

        return list;
    }

    public Set<RoomUnit> getRoomUnits() {
        return getRoomUnits(null);
    }

    public Set<RoomUnit> getRoomUnits(RoomTile atTile) {
        return this.collectRoomUnits(unit -> atTile == null || unit.getCurrentLocation() == atTile);
    }

    /** The units on a tile, filtered in place: the pathfinder asks this for every tile it tries. */
    public Collection<RoomUnit> getRoomUnitsAt(RoomTile tile) {
        if (tile == null) {
            return new HashSet<>();
        }
        return this.collectRoomUnits(unit -> tile.equals(unit.getCurrentLocation()));
    }

    private Set<RoomUnit> collectRoomUnits(Predicate<RoomUnit> filter) {
        Set<RoomUnit> units = new HashSet<>();

        for (User user : this.currentUsers.values()) {
            if (user != null) {
                this.collectRoomUnit(user.getRoomUnit(), filter, units);
            }
        }

        for (Pet pet : this.currentPets.values()) {
            if (pet != null) {
                this.collectRoomUnit(pet.getRoomUnit(), filter, units);
            }
        }

        for (Bot bot : this.currentBots.values()) {
            if (bot != null) {
                this.collectRoomUnit(bot.getRoomUnit(), filter, units);
            }
        }

        return units;
    }

    private void collectRoomUnit(RoomUnit unit, Predicate<RoomUnit> filter, Set<RoomUnit> units) {
        if (unit != null
                && unit.getRoom() != null
                && unit.getRoom().getId() == this.room.getId()
                && filter.test(unit)) {
            units.add(unit);
        }
    }

    public void giveEffect(User user, int effectId, int duration) {
        if (this.currentUsers.containsKey(user.getUserInfo().getId())) {
            this.giveEffect(user.getRoomUnit(), effectId, duration);
        }
    }

    public void giveEffect(RoomUnit roomUnit, int effectId, int duration) {
        if (duration == -1 || duration == Integer.MAX_VALUE) {
            duration = Integer.MAX_VALUE;
        } else {
            duration += this.room.currentUnixTimestamp();
        }

        if (this.room.isAllowEffects() && roomUnit != null) {
            if (effectId != RIDING_EFFECT_ID) {
                User rider = this.getUserByRoomUnit(roomUnit);

                if (rider != null
                        && rider.getUserInfo() != null
                        && rider.getUserInfo().getRiding() != null) {
                    return;
                }
            }

            roomUnit.setEffectId(effectId, duration);
            this.room.sendComposer(new RoomUserEffectComposer(roomUnit).compose());
        }
    }

    public void checkBedLoveEffect(UserItem bed) {
        if (bed == null || !bed.getBaseItem().allowLay()) return;

        BedProfile bedProfile = new BedProfile(bed);
        if (!bedProfile.isDouble()) return;

        Set<User> usersOnBed = this.getUsersOnItem(bed);

        User male = null;
        User female = null;
        for (User h : usersOnBed) {
            if (h.getRoomUnit() == null || !h.getRoomUnit().hasStatus(RoomUnitStatus.LAY)) continue;
            if (h.getUserInfo().getGender() == UserGender.M && male == null) {
                male = h;
            } else if (h.getUserInfo().getGender() == UserGender.F && female == null) {
                female = h;
            }
        }

        if (male != null && female != null) {
            this.giveEffect(male.getRoomUnit(), BED_LOVE_EFFECT_ID, 5);
            this.giveEffect(female.getRoomUnit(), BED_LOVE_EFFECT_ID, 5);
        }
    }

    public void giveHandItem(User user, int handItem) {
        this.giveHandItem(user.getRoomUnit(), handItem);
    }

    public void giveHandItem(RoomUnit roomUnit, int handItem) {
        roomUnit.setHandItem(handItem);
        this.room.sendComposer(new RoomUserHandItemComposer(roomUnit).compose());

        if (handItem > 0) {
            polaris.hotel.wired.core.WiredManager.triggerUserGetsHandItem(this.room, roomUnit);
        }
    }

    public void idle(User user) {
        user.getRoomUnit().setIdle();

        if (user.getRoomUnit().getDanceType() != DanceType.NONE) {
            this.dance(user, DanceType.NONE);
        }

        this.room.sendComposer(new RoomUnitIdleComposer(user.getRoomUnit()).compose());
        WiredManager.triggerUserIdles(this.room, user.getRoomUnit());
    }

    public void unIdle(User user) {
        if (user == null || user.getRoomUnit() == null) {
            return;
        }

        boolean wasIdle = user.getRoomUnit().isIdle();
        user.getRoomUnit().resetIdleTimer();

        if (wasIdle) {
            this.room.sendComposer(new RoomUnitIdleComposer(user.getRoomUnit()).compose());
            WiredManager.triggerUserUnidles(this.room, user.getRoomUnit());
        }
    }

    public void dance(User user, DanceType danceType) {
        this.dance(user.getRoomUnit(), danceType);
    }

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

    public void teleportUserToItem(User user, UserItem item) {
        this.teleportRoomUnitToLocation(
                user.getRoomUnit(), item.getX(), item.getY(), item.getZ() + Item.getCurrentHeight(item));
    }

    public void teleportUserToLocation(User user, short x, short y) {
        this.teleportRoomUnitToLocation(user.getRoomUnit(), x, y, 0.0);
    }

    public void teleportRoomUnitToItem(RoomUnit roomUnit, UserItem item) {
        this.teleportRoomUnitToLocation(roomUnit, item.getX(), item.getY(), item.getZ() + Item.getCurrentHeight(item));
    }

    public void teleportRoomUnitToLocation(RoomUnit roomUnit, short x, short y) {
        this.teleportRoomUnitToLocation(roomUnit, x, y, 0.0);
    }

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

    public void userEntered(User user) {
        user.getRoomUnit().animateWalk = false;

        // Have pets greet their owner
        synchronized (this.currentPets) {
            for (Pet pet : this.currentPets.values()) {
                if (pet.getUserId() == user.getUserInfo().getId()) {
                    // Pet sees its owner - greet them!
                    pet.say(pet.getPetData().randomVocal(PetVocalsType.GREET_OWNER));
                    pet.addHappiness(10);
                }
            }
        }

        synchronized (this.currentBots) {
            if (user.getUserInfo().getId() != this.room.getOwnerId()) {
                return;
            }
            for (Bot bot : this.currentBots.values()) {
                if (bot instanceof VisitorBot) {
                    ((VisitorBot) bot).onUserEnter(user);
                    break;
                }
            }
        }

        UserItem doorTileTopItem = this.room.getTopItemAt(
                user.getRoomUnit().getX(), user.getRoomUnit().getY());
        if (doorTileTopItem != null
                && !(doorTileTopItem instanceof polaris.hotel.items.interactions.InteractionTeleportTile)) {
            try {
                doorTileTopItem.onWalkOn(user.getRoomUnit(), this.room, new Object[] {});
            } catch (Exception e) {
                LOGGER.error("Caught exception", e);
            }
        }
    }

    public void makeSit(User user) {
        if (user.getRoomUnit() == null) {
            return;
        }

        if (user.getRoomUnit().hasStatus(RoomUnitStatus.SIT)
                || !user.getRoomUnit().canForcePosture()) {
            return;
        }

        this.dance(user, DanceType.NONE);
        user.getRoomUnit().cmdSit = true;
        user.getRoomUnit()
                .setBodyRotation(
                        RoomUserRotation.values()[
                                user.getRoomUnit().getBodyRotation().getValue()
                                        - user.getRoomUnit().getBodyRotation().getValue() % 2]);
        user.getRoomUnit().setStatus(RoomUnitStatus.SIT, 0.5 + "");
        this.room.sendComposer(new RoomUserStatusComposer(user.getRoomUnit()).compose());
    }

    public void makeStand(User user) {
        if (user.getRoomUnit() == null) {
            return;
        }

        UserItem item = this.room.getTopItemAt(
                user.getRoomUnit().getX(), user.getRoomUnit().getY());
        if (item == null
                || !item.getBaseItem().allowSit()
                || !item.getBaseItem().allowLay()) {
            user.getRoomUnit().cmdStand = true;
            user.getRoomUnit()
                    .setBodyRotation(
                            RoomUserRotation.values()[
                                    user.getRoomUnit().getBodyRotation().getValue()
                                            - user.getRoomUnit()
                                                            .getBodyRotation()
                                                            .getValue()
                                                    % 2]);
            user.getRoomUnit().removeStatus(RoomUnitStatus.SIT);
            this.room.sendComposer(new RoomUserStatusComposer(user.getRoomUnit()).compose());
        }
    }

    public void dispose() {
        for (User user : this.currentUsers.values()) {
            if (user.getRoomUnit() != null) {
                WiredMoveCarryHelper.cleanupRoomUnit(user.getRoomUnit());
                WiredUserMovementHelper.cleanupRoomUnit(user.getRoomUnit());
            }
        }
        for (Bot bot : this.currentBots.values()) {
            if (bot.getRoomUnit() != null) {
                WiredMoveCarryHelper.cleanupRoomUnit(bot.getRoomUnit());
                WiredUserMovementHelper.cleanupRoomUnit(bot.getRoomUnit());
            }
        }
        for (Pet pet : this.currentPets.values()) {
            if (pet.getRoomUnit() != null) {
                WiredMoveCarryHelper.cleanupRoomUnit(pet.getRoomUnit());
                WiredUserMovementHelper.cleanupRoomUnit(pet.getRoomUnit());
            }
        }
        this.currentUsers.clear();
        this.currentBots.clear();
        this.currentPets.clear();
        this.userQueue.clear();
    }
}
