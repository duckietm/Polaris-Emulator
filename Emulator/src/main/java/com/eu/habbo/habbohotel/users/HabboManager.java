package com.eu.habbo.habbohotel.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.database.SqlQueries;
import com.eu.habbo.habbohotel.modtool.ModToolBan;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.permissions.Rank;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.catalog.*;
import com.eu.habbo.messages.outgoing.catalog.marketplace.MarketplaceConfigComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.GenericAlertComposer;
import com.eu.habbo.messages.outgoing.modtool.ModToolComposer;
import com.eu.habbo.messages.outgoing.users.UserPerksComposer;
import com.eu.habbo.messages.outgoing.users.UserPermissionsComposer;
import com.eu.habbo.plugin.events.users.UserRankChangedEvent;
import com.eu.habbo.plugin.events.users.UserRegisteredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

public class HabboManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(HabboManager.class);

    //Configuration. Loaded from database & updated accordingly.
    public static String WELCOME_MESSAGE = "";
    public static boolean NAMECHANGE_ENABLED = false;

    private final ConcurrentHashMap<Integer, Habbo> onlineHabbos;
    private final ConcurrentHashMap<String, Habbo> onlineHabbosByName;
    private final ConcurrentHashMap<Integer, String> usernameCache = new ConcurrentHashMap<>();

    public HabboManager() {
        long millis = System.currentTimeMillis();

        this.onlineHabbos = new ConcurrentHashMap<>();
        this.onlineHabbosByName = new ConcurrentHashMap<>();

        LOGGER.info("Habbo Manager -> Loaded! ({} MS)", System.currentTimeMillis() - millis);
    }

    public static HabboInfo getOfflineHabboInfo(int id) {
        try {
            return SqlQueries.queryOne(
                    "SELECT * FROM users WHERE id = ? LIMIT 1",
                    HabboInfo::new,
                    id).orElse(null);
        } catch (SqlQueries.DataAccessException e) {
            LOGGER.error("Caught SQL exception", e);
            return null;
        }
    }

    public static HabboInfo getOfflineHabboInfo(String username) {
        try {
            return SqlQueries.queryOne(
                    "SELECT * FROM users WHERE username = ? LIMIT 1",
                    HabboInfo::new,
                    username).orElse(null);
        } catch (SqlQueries.DataAccessException e) {
            LOGGER.error("Caught SQL exception", e);
            return null;
        }
    }

    public void addHabbo(Habbo habbo) {
        this.onlineHabbos.put(habbo.getHabboInfo().getId(), habbo);
        this.onlineHabbosByName.put(habbo.getHabboInfo().getUsername().toLowerCase(), habbo);
    }

    public void removeHabbo(Habbo habbo) {
        this.onlineHabbos.remove(habbo.getHabboInfo().getId());
        this.onlineHabbosByName.remove(habbo.getHabboInfo().getUsername().toLowerCase());
    }

    public Habbo getHabbo(int id) {
        return this.onlineHabbos.get(id);
    }

    public Habbo getHabbo(String username) {
        return this.onlineHabbosByName.get(username.toLowerCase());
    }

    public Habbo loadHabbo(String sso) {
        Habbo habbo;
        int userId = 0;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT id FROM users WHERE auth_ticket = ? AND (auth_ticket_expires_at IS NULL OR auth_ticket_expires_at >= NOW()) LIMIT 1")) {
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

        habbo = this.cloneCheck(userId);
        if (habbo != null) {
            habbo.alert(Emulator.getTexts().getValue("loggedin.elsewhere"));
            Emulator.getGameServer().getGameClientManager().forceDisposeClient(habbo.getClient());
            habbo = null;
        }

        ModToolBan ban = Emulator.getGameEnvironment().getModToolManager().checkForBan(userId);
        if (ban != null) {
            return null;
        }


        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM users WHERE auth_ticket = ? AND (auth_ticket_expires_at IS NULL OR auth_ticket_expires_at >= NOW()) LIMIT 1")) {
            statement.setString(1, sso);
            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    habbo = new Habbo(set);

                    if (habbo.getHabboInfo().firstVisit) {
                        Emulator.getPluginManager().fireEvent(new UserRegisteredEvent(habbo));
                    }

                    // NB: il ticket SSO NON viene svuotato qui di proposito. Dietro
                    // Cloudflare il WebSocket viene droppato e il client ritenta più
                    // volte con lo STESSO ticket: se lo consumassimo al primo uso, i
                    // retry (e l'hard-refresh) fallirebbero con "non-existing SSO token".
                    // Il ticket resta valido fino alla scadenza (auth_ticket_expires_at,
                    // TTL gestito dal CMS) o finché il CMS non ne scrive uno nuovo / logout.
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        } catch (Exception ex) {
            LOGGER.error("Caught exception", ex);
        }

        return habbo;
    }

    public HabboInfo getHabboInfo(int id) {
        if (this.getHabbo(id) == null) {
            return getOfflineHabboInfo(id);
        }
        return this.getHabbo(id).getHabboInfo();
    }

    public String getCachedUsername(int id) {
        String cached = this.usernameCache.get(id);
        if (cached != null) return cached;

        Habbo online = this.getHabbo(id);
        if (online != null) {
            String name = online.getHabboInfo().getUsername();
            this.usernameCache.put(id, name);
            return name;
        }

        HabboInfo offline = getOfflineHabboInfo(id);
        if (offline != null) {
            String name = offline.getUsername();
            this.usernameCache.put(id, name);
            return name;
        }
        return "Unknown";
    }

    public int getOnlineCount() {
        return this.onlineHabbos.size();
    }

    public Habbo cloneCheck(int id) {
        return Emulator.getGameServer().getGameClientManager().getHabbo(id);
    }

    public void sendPacketToHabbosWithPermission(ServerMessage message, String perm) {
        for (Habbo habbo : this.onlineHabbos.values()) {
            if (habbo.hasPermission(perm)) {
                habbo.getClient().sendResponse(message);
            }
        }
    }

    public ConcurrentHashMap<Integer, Habbo> getOnlineHabbos() {
        return this.onlineHabbos;
    }

    public synchronized void dispose() {


//


        LOGGER.info("Habbo Manager -> Disposed!");
    }

    public List<HabboInfo> getCloneAccounts(Habbo habbo, int limit) {
        try {
            return SqlQueries.query(
                    "SELECT * FROM users WHERE (ip_register = ? OR ip_current = ?) AND id != ? ORDER BY id DESC LIMIT ?",
                    HabboInfo::new,
                    habbo.getHabboInfo().getIpRegister(),
                    habbo.getHabboInfo().getIpLogin(),
                    habbo.getHabboInfo().getId(),
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
        Habbo habbo = this.getHabbo(userId);

        if (!Emulator.getGameEnvironment().getPermissionsManager().rankExists(rankId)) {
            throw new Exception("Rank ID (" + rankId + ") does not exist");
        }
        Rank newRank = Emulator.getGameEnvironment().getPermissionsManager().getRank(rankId);
        if (habbo != null && habbo.getHabboStats() != null) {
            Rank oldRank = habbo.getHabboInfo().getRank();
            if (!oldRank.getBadge().isEmpty()) {
                habbo.deleteBadge(habbo.getInventory().getBadgesComponent().getBadge(oldRank.getBadge()));
            }
            if(oldRank.getRoomEffect() > 0) {
                habbo.getInventory().getEffectsComponent().effects.remove(oldRank.getRoomEffect());
            }

            habbo.getHabboInfo().setRank(newRank);

            if (!newRank.getBadge().isEmpty()) {
                habbo.addBadge(newRank.getBadge());
            }

            if(newRank.getRoomEffect() > 0) {
                habbo.getInventory().getEffectsComponent().createRankEffect(habbo.getHabboInfo().getRank().getRoomEffect());
            }

            habbo.getClient().sendResponse(new UserPermissionsComposer(habbo));
            habbo.getClient().sendResponse(new UserPerksComposer(habbo));

            if (habbo.hasPermission(Permission.ACC_SUPPORTTOOL)) {
                habbo.getClient().sendResponse(new ModToolComposer(habbo));
            }
            habbo.getHabboInfo().run();

            habbo.getClient().sendResponse(new CatalogUpdatedComposer());
            habbo.getClient().sendResponse(new CatalogModeComposer(0));
            habbo.getClient().sendResponse(new DiscountComposer());
            habbo.getClient().sendResponse(new MarketplaceConfigComposer());
            habbo.getClient().sendResponse(new GiftConfigurationComposer());
            habbo.getClient().sendResponse(new RecyclerLogicComposer());
            habbo.alert(Emulator.getTexts().getValue("commands.generic.cmd_give_rank.new_rank").replace("id", newRank.getName()));
        } else {
            try {
                SqlQueries.update("UPDATE users SET `rank` = ? WHERE id = ? LIMIT 1", rankId, userId);
            } catch (SqlQueries.DataAccessException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        }

        Emulator.getPluginManager().fireEvent(new UserRankChangedEvent(habbo));
    }

    public void giveCredits(int userId, int credits) {
        Habbo habbo = this.getHabbo(userId);
        if (habbo != null) {
            habbo.giveCredits(credits);
        } else {
            try {
                SqlQueries.update("UPDATE users SET credits = credits + ? WHERE id = ? LIMIT 1", credits, userId);
            } catch (SqlQueries.DataAccessException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        }
    }

    public void staffAlert(String message) {
        message = Emulator.getTexts().getValue("commands.generic.cmd_staffalert.title") + "\r\n" + message;
        ServerMessage msg = new GenericAlertComposer(message).compose();
        Emulator.getGameEnvironment().getHabboManager().sendPacketToHabbosWithPermission(msg, "cmd_staffalert");
    }
}
