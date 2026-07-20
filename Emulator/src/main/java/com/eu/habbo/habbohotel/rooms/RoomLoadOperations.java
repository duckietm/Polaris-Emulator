package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.items.interactions.InteractionFireworks;
import com.eu.habbo.habbohotel.items.interactions.InteractionJukeBox;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.pets.PetManager;
import com.eu.habbo.habbohotel.users.DanceType;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.plugin.events.rooms.RoomLoadedEvent;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class RoomLoadOperations implements RoomLoader.Operations {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoomLoadOperations.class);

    private final Room room;
    private final RoomDependencies.ConnectionProvider database;

    RoomLoadOperations(Room room, RoomDependencies.ConnectionProvider database) {
        this.room = room;
        this.database = database;
    }

    @Override
    public int roomId() {
        return this.room.getId();
    }

    @Override
    public boolean prepare(long generation) {
        return this.room.prepareLoadTransition(generation);
    }

    @Override
    public void initialize() {
        synchronized (this.room.roomUnitLock) {
            this.room.getUnitManager().clear();
        }
        this.room.replaceSpecialTypes(new RoomSpecialTypes());
    }

    @Override
    public void loadLayout() {
        try {
            synchronized (this.room) {
                if (this.room.getLayout() != null) {
                    return;
                }

                RoomLayout layout = this.room.hasCustomLayout()
                        ? this.room.gameEnvironment().getRoomManager().loadCustomLayout(this.room)
                        : this.room.gameEnvironment().getRoomManager().loadLayout(this.room.layoutName(), this.room);
                this.room.setLayout(layout);
            }
        } catch (Exception exception) {
            LOGGER.error("Caught exception loading layout", exception);
        }
    }

    @Override
    public boolean shouldLoadPromotion() {
        return this.room.getPromotionManager().getPromotedFlag();
    }

    @Override
    public void loadPromotion() {
        try (Connection connection = this.database.openConnection()) {
            this.room.getPromotionManager().loadPromotion(true, connection);
        } catch (Exception exception) {
            LOGGER.error("Caught exception loading promotion", exception);
        }
    }

    @Override
    public void loadItems() {
        this.withConnection("Caught exception loading items", connection -> {
            synchronized (this.room) {
                this.room.getItemManager().loadItems(connection);
            }
        });
    }

    @Override
    public void loadRights() {
        this.withConnection("Caught exception loading rights", this.room.getRightsManager()::loadRights);
    }

    @Override
    public void loadWordFilter() {
        this.withConnection("Caught exception loading word filter", connection -> {
            synchronized (this.room) {
                this.room.getChatManager().loadWordFilter(connection);
            }
        });
    }

    @Override
    public void loadBots() {
        this.withConnection("Caught exception loading bots", this::loadBots);
    }

    @Override
    public void loadPets() {
        this.withConnection("Caught exception loading pets", this::loadPets);
    }

    @Override
    public void loadHeightmap() {
        try {
            synchronized (this.room) {
                RoomLayout layout = this.room.getLayout();
                if (layout == null) {
                    LOGGER.error("Unknown Room Layout for Room (ID: {})", this.room.getId());
                    return;
                }

                for (short x = 0; x < layout.getMapSizeX(); x++) {
                    for (short y = 0; y < layout.getMapSizeY(); y++) {
                        RoomTile tile = layout.getTile(x, y);
                        if (tile != null) {
                            this.room.updateTile(tile);
                        }
                    }
                }
            }
        } catch (Exception exception) {
            LOGGER.error("Caught exception loading heightmap", exception);
        }
    }

    @Override
    public void loadWiredData() {
        this.withConnection("Caught exception loading wired data", connection -> {
            synchronized (this.room) {
                this.room.getItemManager().loadWiredData(connection);
            }
        });
    }

    @Override
    public void resetIdleCycles() {
        this.room.getCycleManager().resetIdleCycles();
    }

    @Override
    public boolean finish(long generation) {
        TraxManager traxManager = new TraxManager(this.room, this.database);
        this.room.replaceTraxManager(traxManager);

        RoomSpecialTypes specialTypes = this.room.getRoomSpecialTypes();
        if (this.room.isJukeboxActive()) {
            traxManager.play(0);
            for (HabboItem item : specialTypes.getItemsOfType(InteractionJukeBox.class)) {
                item.setExtradata("1");
                this.room.updateItem(item);
            }
        }

        for (HabboItem item : specialTypes.getItemsOfType(InteractionFireworks.class)) {
            item.setExtradata("1");
            this.room.updateItem(item);
        }

        synchronized (this.room) {
            try {
                if (this.room.publishLoadTransition(
                        generation,
                        () -> this.room
                                .threading()
                                .getService()
                                .scheduleAtFixedRate(this.room, 500, 500, TimeUnit.MILLISECONDS))) {
                    this.room.pluginManager().fireEvent(new RoomLoadedEvent(this.room));
                    return true;
                }
            } catch (Exception exception) {
                this.room.failLoadTransition(generation);
                LOGGER.error("Caught exception publishing room load", exception);
            }
        }
        return false;
    }

    @Override
    public void reportFailure(String message, Exception exception) {
        LOGGER.error(message, exception);
    }

    private void loadBots(Connection connection) {
        synchronized (this.room) {
            this.room.getUnitManager().clearBots();

            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT users.username AS owner_name, bots.* FROM bots INNER JOIN users ON bots.user_id = users.id WHERE room_id = ?")) {
                statement.setInt(1, this.room.getId());
                try (ResultSet set = statement.executeQuery()) {
                    while (set.next()) {
                        Bot bot = this.room.gameEnvironment().getBotManager().loadBot(set);
                        if (bot != null) {
                            this.initializeBot(bot, set);
                        }
                    }
                }
            } catch (SQLException exception) {
                LOGGER.error("Caught SQL exception", exception);
            }
        }
    }

    private void initializeBot(Bot bot, ResultSet set) throws SQLException {
        bot.setRoom(this.room);
        bot.setRoomUnit(new RoomUnit());
        bot.getRoomUnit().setPathFinderRoom(this.room);
        bot.getRoomUnit().setLocation(this.room.getLayout().getTile((short) set.getInt("x"), (short) set.getInt("y")));
        if (bot.getRoomUnit().getCurrentLocation() == null) {
            bot.getRoomUnit().setLocation(this.room.getLayout().getDoorTile());
            bot.getRoomUnit()
                    .setRotation(
                            RoomUserRotation.fromValue(this.room.getLayout().getDoorDirection()));
        } else {
            bot.getRoomUnit().setZ(set.getDouble("z"));
            bot.getRoomUnit().setPreviousLocationZ(set.getDouble("z"));
            bot.getRoomUnit().setRotation(RoomUserRotation.values()[set.getInt("rot")]);
        }
        bot.getRoomUnit().setRoomUnitType(RoomUnitType.BOT);
        bot.getRoomUnit().setDanceType(DanceType.values()[set.getInt("dance")]);
        bot.getRoomUnit().setInRoom(true);
        this.room.giveEffect(bot.getRoomUnit(), set.getInt("effect"), Integer.MAX_VALUE);
        this.room.addBot(bot);
    }

    private void loadPets(Connection connection) {
        synchronized (this.room) {
            this.room.getUnitManager().clearPets();

            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT users.username as pet_owner_name, users_pets.* FROM users_pets INNER JOIN users ON users_pets.user_id = users.id WHERE room_id = ?")) {
                statement.setInt(1, this.room.getId());
                try (ResultSet set = statement.executeQuery()) {
                    while (set.next()) {
                        try {
                            this.initializePet(PetManager.loadPet(set), set);
                        } catch (SQLException exception) {
                            LOGGER.error("Caught SQL exception", exception);
                        }
                    }
                }
            } catch (SQLException exception) {
                LOGGER.error("Caught SQL exception", exception);
            }
        }
    }

    private void initializePet(Pet pet, ResultSet set) throws SQLException {
        pet.setRoom(this.room);
        pet.setRoomUnit(new RoomUnit());
        pet.getRoomUnit().setPathFinderRoom(this.room);
        pet.getRoomUnit().setLocation(this.room.getLayout().getTile((short) set.getInt("x"), (short) set.getInt("y")));
        if (pet.getRoomUnit().getCurrentLocation() == null) {
            pet.getRoomUnit().setLocation(this.room.getLayout().getDoorTile());
            pet.getRoomUnit()
                    .setRotation(
                            RoomUserRotation.fromValue(this.room.getLayout().getDoorDirection()));
        } else {
            pet.getRoomUnit().setZ(set.getDouble("z"));
            pet.getRoomUnit().setRotation(RoomUserRotation.values()[set.getInt("rot")]);
        }
        pet.getRoomUnit().setRoomUnitType(RoomUnitType.PET);
        pet.getRoomUnit().setCanWalk(true);
        this.room.addPet(pet);
        this.room.getFurniOwnerNames().put(pet.getUserId(), set.getString("pet_owner_name"));
    }

    private void withConnection(String failureMessage, ConnectionOperation operation) {
        try (Connection connection = this.database.openConnection()) {
            operation.load(connection);
        } catch (Exception exception) {
            LOGGER.error(failureMessage, exception);
        }
    }

    @FunctionalInterface
    private interface ConnectionOperation {
        void load(Connection connection) throws Exception;
    }
}
