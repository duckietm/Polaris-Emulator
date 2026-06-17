package com.eu.habbo.habbohotel.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.database.SqlQueries;
import com.eu.habbo.habbohotel.catalog.CatalogItem;
import com.eu.habbo.habbohotel.games.Game;
import com.eu.habbo.habbohotel.games.GamePlayer;
import com.eu.habbo.habbohotel.messenger.MessengerCategory;
import com.eu.habbo.habbohotel.navigation.NavigatorSavedSearch;
import com.eu.habbo.habbohotel.permissions.Rank;
import com.eu.habbo.habbohotel.pets.PetTasks;
import com.eu.habbo.habbohotel.pets.RideablePet;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;
import gnu.trove.map.hash.TIntIntHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class HabboInfo implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(HabboInfo.class);

    public boolean firstVisit = false;
    private String username;
    private String motto;
    private String look;
    private HabboGender gender;
    private String mail;
    private String sso;
    private String ipRegister;
    private String ipLogin;
    private int id;
    private int accountCreated;
    private Rank rank;
    private int credits;
    private int lastOnline;
    private int homeRoom;
    private boolean online;
    private int InfostandBg;
    private int InfostandStand;
    private int InfostandOverlay;
    private int InfostandCardBg;
    private int InfostandBorder;
    private int loadingRoom;
    private Room currentRoom;
    private String roomEntryMethod = "door";
    private int roomEntryTeleportId = 0;
    private int roomQueueId;
    private RideablePet riding;
    private Class<? extends Game> currentGame;
    private TIntIntHashMap currencies;
    // Serializes credits + currencies read-modify-write and the saveCurrencies
    // snapshot so the credit-roller thread and purchase/trade handler threads
    // can't lose updates or rehash the Trove map mid-iteration. Never held
    // across run()'s DB I/O.
    private final Object currencyLock = new Object();
    private GamePlayer gamePlayer;
    private int photoRoomId;
    private int photoTimestamp;
    private String photoURL;
    private String photoJSON;
    private int webPublishTimestamp;
    private String machineID;
    private List<NavigatorSavedSearch> savedSearches = new ArrayList<>();
    private List<MessengerCategory> messengerCategories = new ArrayList<>();

    public HabboInfo(ResultSet set) {
        try {
            this.id = set.getInt("id");
            this.username = set.getString("username");
            this.motto = set.getString("motto");
            this.look = set.getString("look");
            this.gender = HabboGender.valueOf(set.getString("gender"));
            this.mail = set.getString("mail");
            this.sso = set.getString("auth_ticket");
            this.ipRegister = set.getString("ip_register");
            this.ipLogin = set.getString("ip_current");
            this.rank = Emulator.getGameEnvironment().getPermissionsManager().getRank(set.getInt("rank"));

            if (this.rank == null) {
                LOGGER.error("No existing rank found with id " + set.getInt("rank") + ". Make sure an entry in the permissions table exists.");
                LOGGER.warn(this.username + " has an invalid rank with id " + set.getInt("rank") + ". Make sure an entry in the permissions table exists.");
                this.rank = Emulator.getGameEnvironment().getPermissionsManager().getRank(1);
            }

            this.accountCreated = set.getInt("account_created");
            this.credits = set.getInt("credits");
            this.homeRoom = set.getInt("home_room");
            this.lastOnline = set.getInt("last_online");
            this.machineID = set.getString("machine_id");
            this.online = false;
            this.InfostandBg = set.getInt("background_id");
            this.InfostandStand = set.getInt("background_stand_id");
            this.InfostandOverlay = set.getInt("background_overlay_id");
            this.InfostandCardBg = set.getInt("background_card_id");
            try {
                this.InfostandBorder = set.getInt("background_border_id");
            } catch (SQLException ignored) {
                this.InfostandBorder = 0;
            }
            this.currentRoom = null;
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        this.loadCurrencies();
        this.loadSavedSearches();
        this.loadMessengerCategories();
    }

    private void loadCurrencies() {
        this.currencies = new TIntIntHashMap();

        try {
            SqlQueries.forEach(
                    "SELECT * FROM users_currency WHERE user_id = ?",
                    rs -> this.currencies.put(rs.getInt("type"), rs.getInt("amount")),
                    this.id);
        } catch (SqlQueries.DataAccessException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    private void saveCurrencies() {
        // Snapshot under the lock so a concurrent adjustOrPutValue/put can't
        // rehash the Trove map while we iterate; do the DB batch off-lock.
        List<int[]> entries;
        synchronized (this.currencyLock) {
            entries = new ArrayList<>(this.currencies.size());
            this.currencies.forEachEntry((type, amount) -> {
                entries.add(new int[]{type, amount});
                return true;
            });
        }

        try {
            SqlQueries.batchUpdate(
                    "INSERT INTO users_currency (user_id, type, amount) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE amount = ?",
                    entries,
                    (ps, e) -> {
                        ps.setInt(1, this.id);
                        ps.setInt(2, e[0]);
                        ps.setInt(3, e[1]);
                        ps.setInt(4, e[1]);
                    });
        } catch (SqlQueries.DataAccessException ex) {
            LOGGER.error("Caught SQL exception", ex);
        }
    }

    private void loadSavedSearches() {
        try {
            this.savedSearches = SqlQueries.query(
                    "SELECT * FROM users_saved_searches WHERE user_id = ?",
                    rs -> new NavigatorSavedSearch(rs.getString("search_code"), rs.getString("filter"), rs.getInt("id")),
                    this.id);
        } catch (SqlQueries.DataAccessException e) {
            LOGGER.error("Caught SQL exception", e);
            this.savedSearches = new ArrayList<>();
        }
    }

    public void addSavedSearch(NavigatorSavedSearch search) {
        this.savedSearches.add(search);

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("INSERT INTO users_saved_searches (search_code, filter, user_id) VALUES (?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, search.getSearchCode());
            statement.setString(2, search.getFilter());
            statement.setInt(3, this.id);
            int affectedRows = statement.executeUpdate();

            if (affectedRows == 0) {
                throw new SQLException("Creating saved search failed, no rows affected.");
            }

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    search.setId(generatedKeys.getInt(1));
                } else {
                    throw new SQLException("Creating saved search failed, no ID found.");
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public void deleteSavedSearch(NavigatorSavedSearch search) {
        this.savedSearches.remove(search);

        try {
            SqlQueries.update("DELETE FROM users_saved_searches WHERE id = ?", search.getId());
        } catch (SqlQueries.DataAccessException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    private void loadMessengerCategories() {
        try {
            this.messengerCategories = SqlQueries.query(
                    "SELECT * FROM messenger_categories WHERE user_id = ?",
                    rs -> new MessengerCategory(rs.getString("name"), rs.getInt("user_id"), rs.getInt("id")),
                    this.id);
        } catch (SqlQueries.DataAccessException e) {
            LOGGER.error("Caught SQL exception", e);
            this.messengerCategories = new ArrayList<>();
        }
    }

    public void addMessengerCategory(MessengerCategory category) {
        this.messengerCategories.add(category);

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("INSERT INTO messenger_categories (name, user_id) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, category.getName());
            statement.setInt(2, this.id);
            int affectedRows = statement.executeUpdate();

            if (affectedRows == 0) {
                throw new SQLException("Creating messenger category failed, no rows affected.");
            }

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    category.setId(generatedKeys.getInt(1));
                } else {
                    throw new SQLException("Creating messenger category failed, no ID found.");
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public void deleteMessengerCategory(MessengerCategory category) {
        this.messengerCategories.remove(category);

        try {
            SqlQueries.update("DELETE FROM messenger_categories WHERE id = ?", category.getId());
        } catch (SqlQueries.DataAccessException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public int getCurrencyAmount(int type) {
        synchronized (this.currencyLock) {
            return this.currencies.get(type);
        }
    }

    public TIntIntHashMap getCurrencies() {
        // Return a snapshot under the lock: callers iterate this map, which would
        // otherwise corrupt during a concurrent adjustOrPutValue rehash.
        synchronized (this.currencyLock) {
            return new TIntIntHashMap(this.currencies);
        }
    }

    public void addCurrencyAmount(int type, int amount) {
        synchronized (this.currencyLock) {
            this.currencies.adjustOrPutValue(type, amount, amount);
        }
        this.run();
    }

    public void setCurrencyAmount(int type, int amount) {
        synchronized (this.currencyLock) {
            this.currencies.put(type, amount);
        }
        this.run();
    }

    public int getId() {
        return this.id;
    }

    public String getUsername() {
        return this.username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getMotto() {
        return this.motto;
    }

    public void setMotto(String motto) {
        this.motto = motto;
    }

    public int getInfostandBg() {
        return InfostandBg;
    }

    public void setInfostandBg(int infostandBg) {
        InfostandBg = infostandBg;
    }

    public int getInfostandStand() {
        return InfostandStand;
    }

    public void setInfostandStand(int infostandStand) {
        InfostandStand = infostandStand;
    }

    public int getInfostandOverlay() {
        return InfostandOverlay;
    }

    public void setInfostandOverlay(int infostandOverlay) {
        InfostandOverlay = infostandOverlay;
    }

    public int getInfostandCardBg() {
        return InfostandCardBg;
    }

    public void setInfostandCardBg(int infostandCardBg) {
        InfostandCardBg = infostandCardBg;
    }

    public int getInfostandBorder() {
        return InfostandBorder;
    }

    public void setInfostandBorder(int infostandBorder) {
        InfostandBorder = infostandBorder;
    }

    public Rank getRank() {
        return this.rank;
    }

    public void setRank(Rank rank) {
        this.rank = rank;
    }

    public String getLook() {
        return this.look;
    }

    public void setLook(String look) {
        this.look = look;
    }

    public HabboGender getGender() {
        return this.gender;
    }

    public void setGender(HabboGender gender) {
        this.gender = gender;
    }

    public String getMail() {
        return this.mail;
    }

    public void setMail(String mail) {
        this.mail = mail;
    }

    public String getSso() {
        return this.sso;
    }

    public void setSso(String sso) {
        this.sso = sso;
    }

    public String getIpRegister() {
        return this.ipRegister;
    }

    public void setIpRegister(String ipRegister) {
        this.ipRegister = ipRegister;
    }

    public String getIpLogin() {
        return this.ipLogin;
    }

    public void setIpLogin(String ipLogin) {
        this.ipLogin = ipLogin;
    }

    public int getAccountCreated() {
        return this.accountCreated;
    }

    public void setAccountCreated(int accountCreated) {
        this.accountCreated = accountCreated;
    }

    public boolean canBuy(CatalogItem item) {
        return this.getCredits() >= item.getCredits() && this.getCurrencyAmount(item.getPointsType()) >= item.getPoints();
    }

    public int getCredits() {
        synchronized (this.currencyLock) {
            return this.credits;
        }
    }

    public void setCredits(int credits) {
        synchronized (this.currencyLock) {
            this.credits = credits;
        }
        this.run();
    }

    public void addCredits(int credits) {
        synchronized (this.currencyLock) {
            this.credits += credits;
        }
        this.run();
    }

    public int getPixels() {
        return this.getCurrencyAmount(0);
    }

    public void setPixels(int pixels) {
        this.setCurrencyAmount(0, pixels);
    }

    public void addPixels(int pixels) {
        this.addCurrencyAmount(0, pixels);
    }

    public int getLastOnline() {
        return this.lastOnline;
    }

    public void setLastOnline(int lastOnline) {
        this.lastOnline = lastOnline;
    }

    public int getHomeRoom() {
        return this.homeRoom;
    }

    public void setHomeRoom(int homeRoom) {
        this.homeRoom = homeRoom;
    }

    public boolean isOnline() {
        return this.online;
    }

    public void setOnline(boolean value) {
        this.online = value;
    }

    public int getLoadingRoom() {
        return this.loadingRoom;
    }

    public void setLoadingRoom(int loadingRoom) {
        this.loadingRoom = loadingRoom;
    }

    public Room getCurrentRoom() {
        return this.currentRoom;
    }

    public void setCurrentRoom(Room room) {
        this.currentRoom = room;
    }

    public String getRoomEntryMethod() {
        return this.roomEntryMethod;
    }

    public void setRoomEntryMethod(String roomEntryMethod) {
        this.roomEntryMethod = roomEntryMethod;
    }

    public int getRoomEntryTeleportId() {
        return this.roomEntryTeleportId;
    }

    public void setRoomEntryTeleportId(int roomEntryTeleportId) {
        this.roomEntryTeleportId = roomEntryTeleportId;
    }

    public int getRoomQueueId() {
        return this.roomQueueId;
    }

    public void setRoomQueueId(int roomQueueId) {
        this.roomQueueId = roomQueueId;
    }

    public RideablePet getRiding() {
        return this.riding;
    }

    public void setRiding(RideablePet riding) {
        this.riding = riding;
    }

    public void dismountPet() {
        this.dismountPet(false);
    }

    public void dismountPet(boolean isRemoving) {
        if (this.getRiding() == null)
            return;

        Habbo habbo = this.getCurrentRoom().getHabbo(this.getId());
        if (habbo == null)
            return;

        RideablePet riding = this.getRiding();

        riding.setRider(null);
        riding.setTask(PetTasks.FREE);
        this.setRiding(null);

        Room room = this.getCurrentRoom();
        if (room != null)
            room.giveEffect(habbo, 0, -1);

        RoomUnit roomUnit = habbo.getRoomUnit();
        if (roomUnit == null)
            return;

        roomUnit.setZ(riding.getRoomUnit().getZ());
        roomUnit.setPreviousLocationZ(riding.getRoomUnit().getZ());
        roomUnit.stopWalking();
        if (riding.getRoomUnit() != null) {
            riding.getRoomUnit().setCanWalk(true);
            room.sendComposer(new RoomUserStatusComposer(riding.getRoomUnit()).compose());
        }
        room.sendComposer(new RoomUserStatusComposer(roomUnit).compose());
        List<RoomTile> availableTiles = isRemoving ? new ArrayList<>() : this.getCurrentRoom().getLayout().getWalkableTilesAround(roomUnit.getCurrentLocation());

        RoomTile tile = availableTiles.isEmpty() ? roomUnit.getCurrentLocation() : availableTiles.get(0);
        roomUnit.setGoalLocation(tile);
        roomUnit.statusUpdate(true);
    }

    public Class<? extends Game> getCurrentGame() {
        return this.currentGame;
    }

    public void setCurrentGame(Class<? extends Game> currentGame) {
        this.currentGame = currentGame;
    }

    public boolean isInGame() {
        return this.currentGame != null;
    }

    public synchronized GamePlayer getGamePlayer() {
        return this.gamePlayer;
    }

    public synchronized void setGamePlayer(GamePlayer gamePlayer) {
        this.gamePlayer = gamePlayer;
    }

    public int getPhotoRoomId() {
        return this.photoRoomId;
    }

    public void setPhotoRoomId(int roomId) {
        this.photoRoomId = roomId;
    }

    public int getPhotoTimestamp() {
        return this.photoTimestamp;
    }

    public void setPhotoTimestamp(int photoTimestamp) {
        this.photoTimestamp = photoTimestamp;
    }

    public String getPhotoURL() {
        return this.photoURL;
    }

    public void setPhotoURL(String photoURL) {
        this.photoURL = photoURL;
    }

    public String getPhotoJSON() {
        return this.photoJSON;
    }

    public void setPhotoJSON(String photoJSON) {
        this.photoJSON = photoJSON;
    }

    public int getWebPublishTimestamp() {
        return this.webPublishTimestamp;
    }

    public void setWebPublishTimestamp(int webPublishTimestamp) {
        this.webPublishTimestamp = webPublishTimestamp;
    }

    public String getMachineID() {
        return this.machineID;
    }

    public void setMachineID(String machineID) {
        this.machineID = machineID;
    }

    public List<NavigatorSavedSearch> getSavedSearches() {
        return this.savedSearches;
    }

    public List<MessengerCategory> getMessengerCategories() { return this.messengerCategories; }

    @Override
    public void run() {
        this.saveCurrencies();

        // Read credits under the lock so the persisted value is consistent with
        // concurrent addCredits/setCredits (matches the currencyLock invariant).
        final int creditsForSave;
        synchronized (this.currencyLock) {
            creditsForSave = this.credits;
        }

        try {
            SqlQueries.update(
                    "UPDATE users SET motto = ?, online = ?, look = ?, gender = ?, credits = ?, last_login = ?, last_online = ?, home_room = ?, ip_current = ?, `rank` = ?, machine_id = ?, username = ?, background_id = ?, background_stand_id = ?, background_overlay_id = ?, background_card_id = ?, background_border_id = ? WHERE id = ?",
                    this.motto,
                    this.online ? "1" : "0",
                    this.look,
                    this.gender.name(),
                    creditsForSave,
                    Emulator.getIntUnixTimestamp(),
                    this.lastOnline,
                    this.homeRoom,
                    this.ipLogin,
                    this.rank != null ? this.rank.getId() : 1,
                    this.machineID,
                    this.username,
                    this.InfostandBg,
                    this.InfostandStand,
                    this.InfostandOverlay,
                    this.InfostandCardBg,
                    this.InfostandBorder,
                    this.id);
        } catch (SqlQueries.DataAccessException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public int getBonusRarePoints() {
        return this.getCurrencyAmount(Emulator.getConfig().getInt("hotelview.promotional.points.type"));
    }

    public HabboStats getHabboStats() {
        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(this.getId());
        if (habbo != null) {
            return habbo.getHabboStats();
        }

        return HabboStats.load(this);
    }
}
