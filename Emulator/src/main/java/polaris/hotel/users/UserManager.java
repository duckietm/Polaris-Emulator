package polaris.hotel.users;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import javax.sql.rowset.CachedRowSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.database.DetachedRows;
import polaris.database.SqlQueries;
import polaris.hotel.economy.EconomyLedger;
import polaris.hotel.economy.EconomyOperation;
import polaris.hotel.economy.EconomyOperationId;
import polaris.hotel.habbicons.HabbiconService;
import polaris.hotel.modtool.ModToolBan;
import polaris.hotel.permissions.Permission;
import polaris.hotel.permissions.PermissionAuditLog;
import polaris.hotel.permissions.Rank;
import polaris.hotel.permissions.TemporaryRanks;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.catalog.CatalogModeComposer;
import polaris.messages.outgoing.catalog.CatalogUpdatedComposer;
import polaris.messages.outgoing.catalog.DiscountComposer;
import polaris.messages.outgoing.catalog.GiftConfigurationComposer;
import polaris.messages.outgoing.catalog.RecyclerLogicComposer;
import polaris.messages.outgoing.catalog.marketplace.MarketplaceConfigComposer;
import polaris.messages.outgoing.generic.alerts.GenericAlertComposer;
import polaris.messages.outgoing.handshake.DisconnectReasonComposer;
import polaris.messages.outgoing.modtool.ModToolComposer;
import polaris.messages.outgoing.users.UserPerksComposer;
import polaris.messages.outgoing.users.UserPermissionsComposer;
import polaris.plugin.events.users.UserRankChangedEvent;
import polaris.plugin.events.users.UserRegisteredEvent;

public class UserManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserManager.class);

    // Configuration. Loaded from database & updated accordingly.
    public static volatile String WELCOME_MESSAGE = "";
    public static boolean NAMECHANGE_ENABLED = false;

    private final ConcurrentHashMap<Integer, User> onlineUsers;
    private final ConcurrentHashMap<String, User> onlineUsersByName;
    private final ConcurrentHashMap<Integer, String> usernameCache = new ConcurrentHashMap<>();
    private final DisconnectPersistenceGate disconnectPersistence;
    private final HabbiconService habbiconService;

    public UserManager() {
        this(Runnable::run);
    }

    public UserManager(Executor persistenceExecutor) {
        this(persistenceExecutor, null);
    }

    public UserManager(Executor persistenceExecutor, HabbiconService habbiconService) {
        this.habbiconService = habbiconService;
        long millis = System.currentTimeMillis();

        this.onlineUsers = new ConcurrentHashMap<>();
        this.onlineUsersByName = new ConcurrentHashMap<>();
        this.disconnectPersistence = new DisconnectPersistenceGate(persistenceExecutor);

        LOGGER.info("User Manager -> Loaded! ({} MS)", System.currentTimeMillis() - millis);
    }

    public static UserInfo getOfflineUserInfo(int id) {
        return offlineUserInfo("SELECT * FROM users WHERE id = ? LIMIT 1", statement -> statement.setInt(1, id));
    }

    public static UserInfo getOfflineUserInfo(String username) {
        return offlineUserInfo(
                "SELECT * FROM users WHERE username = ? LIMIT 1", statement -> statement.setString(1, username));
    }

    /** UserInfo loads currencies and searches itself, so it is built after the row's connection is back. */
    private static UserInfo offlineUserInfo(String sql, DetachedRows.Binder binder) {
        try (CachedRowSet row = DetachedRows.read(sql, binder)) {
            return row.next() ? new UserInfo(row) : null;
        } catch (SQLException | SqlQueries.DataAccessException e) {
            LOGGER.error("Caught SQL exception", e);
            return null;
        }
    }

    public void addUser(User user) {
        user.setHabbiconService(this.habbiconService);
        this.onlineUsers.put(user.getUserInfo().getId(), user);
        this.onlineUsersByName.put(user.getUserInfo().getUsername().toLowerCase(), user);
    }

    /**
     * Forgets this user object only: a session that expires after the same user logged in again must not take the new
     * one off the online list.
     */
    public void removeUser(User user) {
        this.onlineUsers.remove(user.getUserInfo().getId(), user);
        this.onlineUsersByName.remove(user.getUserInfo().getUsername().toLowerCase(), user);
    }

    public User getUser(int id) {
        return this.onlineUsers.get(id);
    }

    public User getUser(String username) {
        return this.onlineUsersByName.get(username.toLowerCase());
    }

    public User loadUser(String sso) {
        int userId = 0;

        // The SSO lookup deliberately ignores auth_ticket_expires_at: tickets are
        // single-use (consumed right after a successful login below), so replay is
        // bounded by consumption, not by a TTL. Third-party CMSes (e.g. AtomCMS)
        // only write auth_ticket, and a stale expiry left over from a built-in
        // issuer used to block their logins. The expiry column remains in use by
        // the emulator's own HTTP session endpoints.
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT id FROM users WHERE auth_ticket = ? LIMIT 1")) {
            statement.setString(1, sso);
            try (ResultSet s = statement.executeQuery()) {
                if (s.next()) {
                    userId = s.getInt("id");
                }
            }
            statement.close();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        User user = loadUser(
                userId, "SELECT * FROM users WHERE auth_ticket = ? LIMIT 1", statement -> statement.setString(1, sso));

        if (user != null) {
            this.consumeSsoTicket(user.getUserInfo().getId());
        }

        return user;
    }

    /**
     * Consumes (clears) a user's SSO ticket after a successful login so it
     * cannot be replayed. Mid-session reconnects are unaffected: GameClient
     * disposal parks the user and SessionResumeManager restores the same
     * ticket for the reconnect grace window ("ticket cleared to '' after
     * login" is exactly the state its restore-guard expects), and newer
     * clients additionally carry a session-recovery token. Hotel owners can
     * keep tickets alive for debugging with emulator setting debug_sso = 1
     * (default 0).
     */
    public void consumeSsoTicket(int userId) {
        if (Emulator.getConfig().getBoolean("debug_sso", false)) {
            return;
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE users SET auth_ticket = '', auth_ticket_expires_at = NULL WHERE id = ? LIMIT 1")) {
            statement.setInt(1, userId);
            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Failed to consume SSO ticket for user " + userId, e);
        }
    }

    public User loadUserById(int userId) {
        return loadUser(userId, "SELECT * FROM users WHERE id = ? LIMIT 1", statement -> statement.setInt(1, userId));
    }

    private User loadUser(int userId, String query, StatementBinder binder) {
        User user;
        if (userId <= 0 || !this.awaitDisconnectPersistence(userId)) {
            return null;
        }

        user = this.cloneCheck(userId);
        if (user != null) {
            Emulator.getGameServer()
                    .getGameClientManager()
                    .disconnectWithReason(user.getClient(), DisconnectReasonComposer.CONCURRENT_LOGIN);
            user = null;
        }

        if (!this.awaitDisconnectPersistence(userId)) {
            return null;
        }

        ModToolBan ban = Emulator.getGameEnvironment().getModToolManager().checkForBan(userId);
        if (ban != null) {
            return null;
        }

        // The user's row is read first and its connection given back: building the Habbo loads its
        // currencies, inventory, messenger and more, each on a connection of its own.
        try (CachedRowSet set = DetachedRows.read(query, binder::bind)) {
            if (set.next()) {
                user = new User(set);

                if (user.getUserInfo().firstVisit) {
                    Emulator.getPluginManager().fireEvent(new UserRegisteredEvent(user));
                }

                // NB: il ticket SSO NON viene svuotato qui di proposito. Dietro
                // Cloudflare il WebSocket viene droppato e il client ritenta più
                // volte con lo STESSO ticket: se lo consumassimo al primo uso, i
                // retry (e l'hard-refresh) fallirebbero con "non-existing SSO token".
                // Il ticket resta valido fino alla scadenza (auth_ticket_expires_at,
                // TTL gestito dal CMS) o finché il CMS non ne scrive uno nuovo / logout.
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        } catch (Exception ex) {
            LOGGER.error("Caught exception", ex);
        }

        return user;
    }

    @FunctionalInterface
    private interface StatementBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    private boolean awaitDisconnectPersistence(int userId) {
        if (this.disconnectPersistence.await(userId)) {
            return true;
        }
        LOGGER.warn("Interrupted while waiting for disconnect persistence for user {}", userId);
        return false;
    }

    DisconnectPersistenceGate.Registration beginDisconnectPersistence(int userId) {
        return this.disconnectPersistence.begin(userId);
    }

    void submitDisconnectPersistence(DisconnectPersistenceGate.Registration registration, Runnable persistence) {
        this.disconnectPersistence.submit(registration, persistence);
    }

    void cancelDisconnectPersistence(DisconnectPersistenceGate.Registration registration) {
        this.disconnectPersistence.cancel(registration);
    }

    public UserInfo getUserInfo(int id) {
        if (this.getUser(id) == null) {
            return getOfflineUserInfo(id);
        }
        return this.getUser(id).getUserInfo();
    }

    public String getCachedUsername(int id) {
        String cached = this.usernameCache.get(id);
        if (cached != null) return cached;

        User online = this.getUser(id);
        if (online != null) {
            String name = online.getUserInfo().getUsername();
            this.usernameCache.put(id, name);
            return name;
        }

        UserInfo offline = getOfflineUserInfo(id);
        if (offline != null) {
            String name = offline.getUsername();
            this.usernameCache.put(id, name);
            return name;
        }
        return "Unknown";
    }

    public int getOnlineCount() {
        return this.onlineUsers.size();
    }

    public User cloneCheck(int id) {
        return Emulator.getGameServer().getGameClientManager().getUser(id);
    }

    public void sendPacketToUsersWithPermission(ServerMessage message, String perm) {
        for (User user : this.onlineUsers.values()) {
            if (user.hasPermission(perm)) {
                user.getClient().sendResponse(message);
            }
        }
    }

    public ConcurrentHashMap<Integer, User> getOnlineUsers() {
        return this.onlineUsers;
    }

    public synchronized void dispose() {

        //

        LOGGER.info("User Manager -> Disposed!");
    }

    public List<UserInfo> getCloneAccounts(User user, int limit) {
        return this.getCloneAccounts(user.getUserInfo(), limit);
    }

    /** Accounts registered from or last seen on the same IP as this one, online or not. */
    public List<UserInfo> getCloneAccounts(UserInfo info, int limit) {
        try {
            return SqlQueries.query(
                    "SELECT * FROM users WHERE (ip_register = ? OR ip_current = ?) AND id != ? ORDER BY id DESC LIMIT ?",
                    UserInfo::new,
                    info.getIpRegister(),
                    info.getIpLogin(),
                    info.getId(),
                    limit);
        } catch (SqlQueries.DataAccessException e) {
            LOGGER.error("Caught SQL exception", e);
            return new ArrayList<>();
        }
    }

    public List<Map.Entry<Integer, String>> getNameChanges(int userId, int limit) {
        try {
            return SqlQueries.query(
                    "SELECT timestamp, new_name FROM namechange_log WHERE user_id = ? ORDER by timestamp DESC LIMIT ?",
                    rs -> new AbstractMap.SimpleEntry<>(rs.getInt("timestamp"), rs.getString("new_name")),
                    userId,
                    limit);
        } catch (SqlQueries.DataAccessException e) {
            LOGGER.error("Caught SQL exception", e);
            return Collections.emptyList();
        }
    }

    public void setRank(int userId, int rankId) throws Exception {
        this.setRank(userId, rankId, PermissionAuditLog.SYSTEM, "system", "system");
    }

    /** Sets the rank and writes it to the permission audit with who did it and how ({@code via}). */
    public void setRank(int userId, int rankId, int actorId, String actorName, String via) throws Exception {
        User user = this.getUser(userId);

        if (!Emulator.getGameEnvironment().getPermissionsManager().rankExists(rankId)) {
            throw new Exception("Rank ID (" + rankId + ") does not exist");
        }
        Rank newRank = Emulator.getGameEnvironment().getPermissionsManager().getRank(rankId);
        int oldRankId = user != null && user.getUserInfo().getRank() != null
                ? user.getUserInfo().getRank().getId()
                : storedRankId(userId);

        try {
            SqlQueries.update("UPDATE users SET `rank` = ? WHERE id = ? LIMIT 1", rankId, userId);
        } catch (SqlQueries.DataAccessException e) {
            // Nothing else may change when the rank was not stored: ending a temporary rank after a failed update
            // dropped its timer and logged a return that never happened, and the user kept the rank for good.
            throw new Exception("Could not store rank " + rankId + " for user " + userId, e);
        }

        // A rank given any other way ends a temporary rank, so it is not undone later.
        if (!TemporaryRanks.keepsTimer(via)) {
            TemporaryRanks.clear(userId);
        }

        if (user != null && user.getUserStats() != null) {
            Rank oldRank = user.getUserInfo().getRank();
            if (!oldRank.getBadge().isEmpty()) {
                user.deleteBadge(user.getInventory().getBadgesComponent().getBadge(oldRank.getBadge()));
            }
            if (oldRank.getRoomEffect() > 0) {
                user.getInventory().getEffectsComponent().effects.remove(oldRank.getRoomEffect());
            }

            user.getUserInfo().setRank(newRank);

            if (!newRank.getBadge().isEmpty()) {
                user.addBadge(newRank.getBadge());
            }

            if (newRank.getRoomEffect() > 0) {
                user.getInventory()
                        .getEffectsComponent()
                        .createRankEffect(user.getUserInfo().getRank().getRoomEffect());
            }

            user.getClient().sendResponse(new UserPermissionsComposer(user));
            user.getClient().sendResponse(new UserPerksComposer(user));

            if (user.hasPermission(Permission.ACC_SUPPORTTOOL)) {
                user.getClient().sendResponse(new ModToolComposer(user));
            }
            user.getUserInfo().run();

            user.getClient().sendResponse(new CatalogUpdatedComposer());
            user.getClient().sendResponse(new CatalogModeComposer(0));
            user.getClient().sendResponse(new DiscountComposer());
            user.getClient().sendResponse(new MarketplaceConfigComposer());
            user.getClient().sendResponse(new GiftConfigurationComposer());
            user.getClient().sendResponse(new RecyclerLogicComposer());
            user.alert(Emulator.getTexts()
                    .getValue("commands.generic.cmd_give_rank.new_rank")
                    .replace("id", newRank.getName()));
        }

        PermissionAuditLog.rankSet(actorId, actorName, userId, oldRankId, rankId, via);

        Emulator.getPluginManager().fireEvent(new UserRankChangedEvent(user));
    }

    private static int storedRankId(int userId) {
        try {
            return SqlQueries.queryOne("SELECT `rank` FROM users WHERE id = ? LIMIT 1", rs -> rs.getInt(1), userId)
                    .orElse(0);
        } catch (SqlQueries.DataAccessException e) {
            LOGGER.error("Caught SQL exception", e);
            return 0;
        }
    }

    public void giveCredits(int userId, int credits) {
        this.applyCredits(
                userId, credits, "system.habbo_manager", EconomyOperationId.create("credits:" + userId), userId);
    }

    /** Grants or takes credits from a user online or not, with who did it and why; false when nothing changed. */
    public boolean applyCredits(int userId, int credits, String reason, String operationId, Integer actorId) {
        if (credits == 0) return false;

        User user = this.getUser(userId);
        if (user != null) {
            return user.applyCredits(credits, reason, operationId, actorId);
        }

        try {
            EconomyLedger.execute(new EconomyOperation(
                    operationId,
                    userId,
                    actorId,
                    credits > 0 ? "credit_grant" : "credit_debit",
                    reason,
                    EconomyLedger.CREDITS,
                    credits,
                    null,
                    "offline"));
            return true;
        } catch (Exception e) {
            LOGGER.error("Unable to apply audited offline credit mutation for user {}", userId, e);
            return false;
        }
    }

    public void staffAlert(String message) {
        message = Emulator.getTexts().getValue("commands.generic.cmd_staffalert.title") + "\r\n" + message;
        ServerMessage msg = new GenericAlertComposer(message).compose();
        Emulator.getGameEnvironment().getUserManager().sendPacketToUsersWithPermission(msg, "cmd_staffalert");
    }
}
