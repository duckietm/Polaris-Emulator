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
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
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
    private Int2IntOpenHashMap currencies;
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
            this.credits = Math.max(0, set.getInt("credits"));
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

    HabboInfo(int id, int credits) {
        this.id = id;
        this.credits = WalletBalanceMath.requireValidBalance(credits);
        this.gender = HabboGender.M;
        this.currencies = new Int2IntOpenHashMap();
    }

    private void loadCurrencies() {
        this.currencies = new Int2IntOpenHashMap();

        try {
            SqlQueries.forEach(
                    "SELECT * FROM users_currency WHERE user_id = ?",
                    rs -> this.currencies.put(rs.getInt("type"), Math.max(0, rs.getInt("amount"))),
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
            for (Int2IntMap.Entry entry : this.currencies.int2IntEntrySet()) {
                entries.add(new int[]{entry.getIntKey(), entry.getIntValue()});
            }
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
        try {
            SqlQueries.update("UPDATE messenger_friendships SET category = 0 WHERE user_one_id = ? AND category = ?", this.id, category.getId());
            if (SqlQueries.update("DELETE FROM messenger_categories WHERE id = ? AND user_id = ?", category.getId(), this.id) > 0) {
                this.messengerCategories.remove(category);
            }
        } catch (SqlQueries.DataAccessException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public MessengerCategory getMessengerCategory(int categoryId) {
        return this.messengerCategories.stream().filter(category -> category.getId() == categoryId).findFirst().orElse(null);
    }

    public boolean renameMessengerCategory(MessengerCategory category, String name) {
        try {
            if (SqlQueries.update("UPDATE messenger_categories SET name = ? WHERE id = ? AND user_id = ?", name, category.getId(), this.id) <= 0) return false;
            category.setName(name);
            return true;
        } catch (SqlQueries.DataAccessException e) {
            LOGGER.error("Caught SQL exception", e);
            return false;
        }
    }

    public boolean moveMessengerFriendToCategory(int friendId, int categoryId) {
        try {
            return SqlQueries.update("UPDATE messenger_friendships SET category = ? WHERE user_one_id = ? AND user_two_id = ?", categoryId, this.id, friendId) > 0;
        } catch (SqlQueries.DataAccessException e) {
            LOGGER.error("Caught SQL exception", e);
            return false;
        }
    }

    public int getCurrencyAmount(int type) {
        synchronized (this.currencyLock) {
            return this.currencies.get(type);
        }
    }

    public Int2IntOpenHashMap getCurrencies() {
        // Return a snapshot under the lock: callers iterate this map, which would
        // otherwise corrupt during a concurrent addTo/put rehash.
        synchronized (this.currencyLock) {
            return new Int2IntOpenHashMap(this.currencies);
        }
    }

    public void addCurrencyAmount(int type, int amount) {
        // Legacy check-then-act entry point: never throw here, because the many
        // existing callers (staff commands, wired, chests, plugins) are not
        // structured to recover from a rejected mutation. Clamp into range and
        // log if a delta would have gone out of bounds. Paths that must reject an
        // out-of-range update use tryAddCurrencyAmount instead.
        synchronized (this.currencyLock) {
            int current = this.currencies.get(type);
            int updated = WalletBalanceMath.clampedBalance(current, amount);
            if ((long) Math.max(0, current) + amount != updated) {
                LOGGER.warn("Clamped out-of-range point balance for user {} (currency type {}): {} + {} -> {}",
                        this.id, type, current, amount, updated);
            }
            this.currencies.put(type, updated);
        }
        this.run();
    }

    public boolean tryAddCurrencyAmount(int type, int amount) {
        synchronized (this.currencyLock) {
            int current = this.currencies.get(type);
            try {
                this.currencies.put(type, WalletBalanceMath.checkedBalance(current, amount));
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        this.run();
        return true;
    }

    /**
     * Atomically debits the two currencies used by a catalog order. The
     * affordability check and both mutations share the wallet lock, so two
     * concurrent purchase paths cannot both spend the same balance.
     */
    public boolean tryDebitCatalogPayment(int credits, int pointsType, int points) {
        if (credits < 0 || points < 0) {
            return false;
        }

        synchronized (this.currencyLock) {
            int currentPoints = this.currencies.get(pointsType);
            if (this.credits < credits || currentPoints < points) {
                return false;
            }

            this.credits -= credits;
            this.currencies.put(pointsType, currentPoints - points);
        }

        this.run();
        return true;
    }

    /** Restores a catalog debit after delivery failed. */
    public void refundCatalogPayment(int credits, int pointsType, int points) {
        if (credits < 0 || points < 0) {
            throw new IllegalArgumentException("catalog refund cannot be negative");
        }

        synchronized (this.currencyLock) {
            this.credits = Math.addExact(this.credits, credits);
            this.currencies.put(pointsType, Math.addExact(this.currencies.get(pointsType), points));
        }

        this.run();
    }

    public void setCurrencyAmount(int type, int amount) {
        synchronized (this.currencyLock) {
            this.currencies.put(type, WalletBalanceMath.requireValidBalance(amount));
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
            this.credits = WalletBalanceMath.requireValidBalance(credits);
        }
        this.run();
    }

    public void addCredits(int credits) {
        // Legacy check-then-act entry point: never throw here (see
        // addCurrencyAmount). Clamp into range and log an out-of-range delta.
        // Paths that must reject an out-of-range update use tryAddCredits.
        synchronized (this.currencyLock) {
            int updated = WalletBalanceMath.clampedBalance(this.credits, credits);
            if ((long) Math.max(0, this.credits) + credits != updated) {
                LOGGER.warn("Clamped out-of-range credit balance for user {}: {} + {} -> {}",
                        this.id, this.credits, credits, updated);
            }
            this.credits = updated;
        }
        this.run();
    }

    public boolean tryAddCredits(int credits) {
        synchronized (this.currencyLock) {
            try {
                this.credits = WalletBalanceMath.checkedBalance(this.credits, credits);
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        this.run();
        return true;
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
