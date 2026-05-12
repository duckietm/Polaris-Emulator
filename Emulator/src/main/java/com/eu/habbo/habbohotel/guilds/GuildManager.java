package com.eu.habbo.habbohotel.guilds;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.guilds.forums.ForumThread;
import com.eu.habbo.habbohotel.guilds.forums.ForumView;
import com.eu.habbo.habbohotel.items.interactions.InteractionGuildFurni;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.guilds.GuildJoinErrorComposer;
import com.eu.habbo.messages.outgoing.guilds.forums.GuildForumDataComposer;
import gnu.trove.TCollections;
import gnu.trove.iterator.TIntObjectIterator;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.THashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.set.hash.THashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

public class GuildManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(GuildManager.class);

    private final THashMap<GuildPartType, THashMap<Integer, GuildPart>> guildParts;

    private final TIntObjectMap<Guild> guilds;

    private final THashSet<ForumView> views = new THashSet<>();

    public GuildManager() {
        long millis = System.currentTimeMillis();
        this.guildParts = new THashMap<GuildPartType, THashMap<Integer, GuildPart>>();
        this.guilds = TCollections.synchronizedMap(new TIntObjectHashMap<Guild>());

        this.loadGuildParts();
        this.loadGuildViews();

        LOGGER.info("Guild Manager -> Loaded! ({} MS)", System.currentTimeMillis() - millis);
    }


    public void loadGuildParts() {
        this.guildParts.clear();

        for (GuildPartType t : GuildPartType.values()) {
            this.guildParts.put(t, new THashMap<>());
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             Statement statement = connection.createStatement();
             ResultSet set = statement.executeQuery("SELECT * FROM guilds_elements")) {
            while (set.next()) {
                this.guildParts.get(GuildPartType.valueOf(set.getString("type").toUpperCase())).put(set.getInt("id"), new GuildPart(set));
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public void loadGuildViews() {
        this.views.clear();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             Statement statement = connection.createStatement();
             ResultSet set = statement.executeQuery("SELECT * FROM guild_forum_views")) {
            while (set.next()) {
                this.views.add(new ForumView(set));
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public Guild createGuild(Habbo habbo, int roomId, String roomName, String name, String description, String badge, int colorOne, int colorTwo) {
        Guild guild = new Guild(habbo.getHabboInfo().getId(), habbo.getHabboInfo().getUsername(), roomId, roomName, name, description, colorOne, colorTwo, badge);

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO guilds (name, description, room_id, user_id, color_one, color_two, badge, date_created) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, name);
                statement.setString(2, description);
                statement.setInt(3, roomId);
                statement.setInt(4, guild.getOwnerId());
                statement.setInt(5, colorOne);
                statement.setInt(6, colorTwo);
                statement.setString(7, badge);
                statement.setInt(8, Emulator.getIntUnixTimestamp());
                statement.execute();

                try (ResultSet set = statement.getGeneratedKeys()) {
                    if (set.next()) {
                        guild.setId(set.getInt(1));
                    }
                }
            }

            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO guilds_members (guild_id, user_id, level_id, member_since) VALUES (?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                statement.setInt(1, guild.getId());
                statement.setInt(2, habbo.getHabboInfo().getId());
                statement.setInt(3, 0);
                statement.setInt(4, Emulator.getIntUnixTimestamp());
                statement.execute();

                try (ResultSet set = statement.getGeneratedKeys()) {
                    if (set.next()) {
                        guild.increaseMemberCount();
                        //guild.addMember(new GuildMember(habbo.getHabboInfo().getId(), habbo.getHabboInfo().getUsername(), habbo.getHabboInfo().getLook(), Emulator.getIntUnixTimestamp(), 2));
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        habbo.getHabboStats().addGuild(guild.getId());

        return guild;
    }


    public void deleteGuild(Guild guild) {
        THashSet<GuildMember> members = this.getGuildMembers(guild);

        for (GuildMember member : members) {
            Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(member.getUserId());

            if (habbo != null) {
                habbo.getHabboStats().removeGuild(guild.getId());

                if (habbo.getHabboStats().guild == guild.getId()) {
                    habbo.getHabboStats().guild = 0;
                }
            }
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            try (PreparedStatement deleteFavourite = connection.prepareStatement("UPDATE users_settings SET guild_id = ? WHERE guild_id = ?")) {
                deleteFavourite.setInt(1, 0);
                deleteFavourite.setInt(2, guild.getId());
                deleteFavourite.execute();
            }

            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM guild_forum_views WHERE guild_id = ?")) {
                statement.setInt(1, guild.getId());
                statement.execute();
            }

            try (PreparedStatement statement = connection.prepareStatement("DELETE c FROM guilds_forums_comments c INNER JOIN guilds_forums_threads t ON c.thread_id = t.id WHERE t.guild_id = ?")) {
                statement.setInt(1, guild.getId());
                statement.execute();
            }

            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM guilds_forums_threads WHERE guild_id = ?")) {
                statement.setInt(1, guild.getId());
                statement.execute();
            }

            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM guilds_members WHERE guild_id = ?")) {
                statement.setInt(1, guild.getId());
                statement.execute();
            }

            try (PreparedStatement statement = connection.prepareStatement("UPDATE rooms SET guild_id = 0 WHERE guild_id = ?")) {
                statement.setInt(1, guild.getId());
                statement.execute();
            }

            try (PreparedStatement statement = connection.prepareStatement("UPDATE items SET guild_id = 0 WHERE guild_id = ?")) {
                statement.setInt(1, guild.getId());
                statement.execute();
            }

            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM guilds WHERE id = ?")) {
                statement.setInt(1, guild.getId());
                statement.execute();
            }

            Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(guild.getRoomId());

            if (room != null) {
                room.setGuild(0);
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        this.guilds.remove(guild.getId());
        ForumThread.clearCacheForGuild(guild.getId());
        GuildForumDataComposer.invalidateUnreadCache(guild.getId());
    }


    public void clearInactiveGuilds() {
        List<Integer> toRemove = new ArrayList<Integer>();
        TIntObjectIterator<Guild> guilds = this.guilds.iterator();
        for (int i = this.guilds.size(); i-- > 0; ) {
            try {
                guilds.advance();
            } catch (NoSuchElementException e) {
                break;
            }

            if (guilds.value().lastRequested < Emulator.getIntUnixTimestamp() - 300) {
                toRemove.add(guilds.value().getId());
            }
        }

        for (Integer i : toRemove) {
            this.guilds.remove(i);
        }
    }


    public void joinGuild(Guild guild, GameClient client, int userId, boolean acceptRequest) {
        boolean error = false;
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(id) as total FROM guilds_members WHERE user_id = ?")) {
                if (userId == 0)
                    statement.setInt(1, client.getHabbo().getHabboInfo().getId());
                else
                    statement.setInt(1, userId);

                try (ResultSet set = statement.executeQuery()) {
                    if (set.next()) {
                        if (set.getInt(1) >= 100) {
                            //TODO Add non acceptRequest errors. See Outgoing.GroupEditFailComposer
                            if (userId == 0)
                                client.sendResponse(new GuildJoinErrorComposer(GuildJoinErrorComposer.GROUP_LIMIT_EXCEED));
                            else
                                client.sendResponse(new GuildJoinErrorComposer(GuildJoinErrorComposer.MEMBER_FAIL_JOIN_LIMIT_EXCEED_NON_HC));

                            error = true;
                        }
                    }
                }
            }

            if (!error) {
                try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(id) as total FROM guilds_members WHERE guild_id = ? AND level_id < 3")) {
                    statement.setInt(1, guild.getId());
                    try (ResultSet set = statement.executeQuery()) {
                        if (set.next()) {
                            if (set.getInt(1) >= 50000) {
                                client.sendResponse(new GuildJoinErrorComposer(GuildJoinErrorComposer.GROUP_FULL));
                                error = true;
                            }
                        }
                    }
                }

                if (userId == 0 && !error) {
                    if (guild.getState() == GuildState.EXCLUSIVE) {
                        try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(id) as total FROM guilds_members WHERE guild_id = ? AND level_id = 3")) {
                            statement.setInt(1, guild.getId());
                            try (ResultSet set = statement.executeQuery()) {
                                if (set.next()) {
                                    if (set.getInt(1) >= 100) {
                                        client.sendResponse(new GuildJoinErrorComposer(GuildJoinErrorComposer.GROUP_NOT_ACCEPT_REQUESTS));
                                        error = true;
                                    }
                                }
                            }
                        }

                        if (!error) {
                            try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(id) as total FROM guilds_members WHERE guild_id = ? AND user_id = ? LIMIT 1")) {
                                statement.setInt(1, guild.getId());
                                statement.setInt(2, client.getHabbo().getHabboInfo().getId());
                                try (ResultSet set = statement.executeQuery()) {
                                    if (set.next()) {
                                        if (set.getInt(1) >= 1) {
                                            error = true;
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (!error) {
                        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO guilds_members (guild_id, user_id, member_since, level_id) VALUES (?, ?, ?, ?)")) {
                            statement.setInt(1, guild.getId());
                            statement.setInt(2, client.getHabbo().getHabboInfo().getId());
                            statement.setInt(3, Emulator.getIntUnixTimestamp());
                            statement.setInt(4, guild.getState() == GuildState.EXCLUSIVE ? GuildRank.REQUESTED.type : GuildRank.MEMBER.type);
                            statement.execute();
                        }
                    }
                } else if (!error) {
                    try (PreparedStatement statement = connection.prepareStatement("UPDATE guilds_members SET level_id = ?, member_since = ? WHERE user_id = ? AND guild_id = ?")) {
                        statement.setInt(1, GuildRank.MEMBER.type);
                        statement.setInt(2, Emulator.getIntUnixTimestamp());
                        statement.setInt(3, userId);
                        statement.setInt(4, guild.getId());
                        statement.execute();
                    }
                }

                if (userId == 0 && !error) {
                    if (guild.getState() == GuildState.EXCLUSIVE)
                        guild.increaseRequestCount();
                    else {
                        guild.increaseMemberCount();
                        client.getHabbo().getHabboStats().addGuild(guild.getId());
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }


    public void setAdmin(Guild guild, int userId) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("UPDATE guilds_members SET level_id = ? WHERE user_id = ? AND guild_id = ? LIMIT 1")) {
            statement.setInt(1, 1);
            statement.setInt(2, userId);
            statement.setInt(3, guild.getId());
            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }


    public void removeAdmin(Guild guild, int userId) {
        if (guild.getOwnerId() == userId)
            return;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("UPDATE guilds_members SET level_id = ? WHERE user_id = ? AND guild_id = ? LIMIT 1")) {
            statement.setInt(1, 2);
            statement.setInt(2, userId);
            statement.setInt(3, guild.getId());
            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }


    public void removeMember(Guild guild, int userId) {
        if (guild.getOwnerId() == userId)
            return;

        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);

        if (habbo != null && habbo.getHabboStats().guild == guild.getId()) {
            habbo.getHabboStats().removeGuild(guild.getId());
            habbo.getHabboStats().guild = 0;
            habbo.getHabboStats().run();
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("DELETE FROM guilds_members WHERE user_id = ? AND guild_id = ? LIMIT 1")) {
            statement.setInt(1, userId);
            statement.setInt(2, guild.getId());
            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }


    public void addGuild(Guild guild) {
        guild.lastRequested = Emulator.getIntUnixTimestamp();
        this.guilds.put(guild.getId(), guild);
    }


    public GuildMember getGuildMember(Guild guild, Habbo habbo) {
        return getGuildMember(guild.getId(), habbo.getHabboInfo().getId());
    }


    public GuildMember getGuildMember(int guildId, int habboId) {
        GuildMember member = null;
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT users.username, users.look, guilds_members.* FROM guilds_members INNER JOIN users ON guilds_members.user_id = users.id WHERE guilds_members.guild_id = ? AND guilds_members.user_id = ? LIMIT 1")) {
            statement.setInt(1, guildId);
            statement.setInt(2, habboId);
            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    member = new GuildMember(set);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return member;
    }


    public THashSet<GuildMember> getGuildMembers(int guildId) {
        return this.getGuildMembers(this.getGuild(guildId));
    }


    THashSet<GuildMember> getGuildMembers(Guild guild) {
        THashSet<GuildMember> guildMembers = new THashSet<GuildMember>();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT users.username, users.look, guilds_members.* FROM guilds_members INNER JOIN users ON guilds_members.user_id = users.id WHERE guilds_members.guild_id = ?")) {
            statement.setInt(1, guild.getId());
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    guildMembers.add(new GuildMember(set));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return guildMembers;
    }


    public ArrayList<GuildMember> getGuildMembers(Guild guild, int page, int levelId, String query) {
        ArrayList<GuildMember> guildMembers = new ArrayList<GuildMember>();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT users.username, users.look, guilds_members.* FROM guilds_members INNER JOIN users ON guilds_members.user_id = users.id WHERE guilds_members.guild_id = ?  " + (rankQuery(levelId)) + " AND users.username LIKE ? ORDER BY level_id, member_since ASC LIMIT ?, ?")) {
            statement.setInt(1, guild.getId());
            statement.setString(2, "%" + query + "%");
            statement.setInt(3, page * 14);
            statement.setInt(4, (page * 14) + 14);

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    guildMembers.add(new GuildMember(set));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return guildMembers;
    }

    public int getGuildMembersCount(Guild guild, int page, int levelId, String query) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM guilds_members INNER JOIN users ON guilds_members.user_id = users.id WHERE guilds_members.guild_id = ?  " + (rankQuery(levelId)) + " AND users.username LIKE ? ORDER BY level_id, member_since ASC")) {
            statement.setInt(1, guild.getId());
            statement.setString(2, "%" + query + "%");

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    return set.getInt(1);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return 0;
    }


    public THashMap<Integer, GuildMember> getOnlyAdmins(Guild guild) {
        THashMap<Integer, GuildMember> guildAdmins = new THashMap<Integer, GuildMember>();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT users.username, users.look, guilds_members.* FROM guilds_members INNER JOIN users ON guilds_members.user_id = users.id WHERE guilds_members.guild_id = ?  " + (rankQuery(1)))) {
            statement.setInt(1, guild.getId());
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    guildAdmins.put(set.getInt("user_id"), new GuildMember(set));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return guildAdmins;
    }

    private String rankQuery(int level) {
        switch (level) {
            case 2:
                return "AND guilds_members.level_id = 3";
            case 1:
                return "AND (guilds_members.level_id = 0 OR guilds_members.level_id = 1)";
            default:
                return "AND guilds_members.level_id >= 0 AND guilds_members.level_id <= 2";
        }
    }


    public Guild getGuild(int guildId) {
        Guild g = this.guilds.get(guildId);

        if (g == null) {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT users.username, rooms.name as room_name, guilds.* FROM guilds INNER JOIN users ON guilds.user_id = users.id INNER JOIN rooms ON rooms.id = guilds.room_id WHERE guilds.id = ? LIMIT 1")) {
                statement.setInt(1, guildId);
                try (ResultSet set = statement.executeQuery()) {
                    if (set.next()) {
                        g = new Guild(set);
                    }
                }
                if (g != null)
                    g.loadMemberCount();
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        }

        if (g != null) {
            g.lastRequested = Emulator.getIntUnixTimestamp();
            if (!this.guilds.containsKey(guildId))
                this.guilds.put(guildId, g);
        }

        return g;
    }

    public List<Guild> getGuilds(int userId) {
        List<Guild> guilds = new ArrayList<Guild>();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT guild_id FROM guilds_members WHERE user_id = ? AND level_id <= 2 ORDER BY member_since ASC")) {
            statement.setInt(1, userId);
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    Guild guild = getGuild(set.getInt("guild_id"));

                    if (guild != null) {
                        guilds.add(guild);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return guilds;
    }

    public List<Guild> getOwnedGuilds(int userId) {
        List<Guild> guilds = new ArrayList<Guild>();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT id FROM guilds WHERE user_id = ?")) {
            statement.setInt(1, userId);
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    Guild guild = getGuild(set.getInt("id"));

                    if (guild != null) {
                        guilds.add(guild);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return guilds;
    }

    public List<Guild> getAllGuilds() {
        List<Guild> guilds = new ArrayList<Guild>();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT id FROM guilds ORDER BY id DESC LIMIT 20")) {
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    Guild guild = getGuild(set.getInt("id"));

                    if (guild != null) {
                        guilds.add(guild);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return guilds;
    }

    public boolean symbolColor(int colorId) {
        for (GuildPart part : this.getSymbolColors()) {
            if (part.id == colorId)
                return true;
        }

        return false;
    }

    public boolean backgroundColor(int colorId) {
        for (GuildPart part : this.getBackgroundColors()) {
            if (part.id == colorId)
                return true;
        }
        return false;
    }

    public THashMap<GuildPartType, THashMap<Integer, GuildPart>> getGuildParts() {
        return this.guildParts;
    }

    public Collection<GuildPart> getBases() {
        return this.guildParts.get(GuildPartType.BASE).values();
    }

    public GuildPart getBase(int id) {
        return this.guildParts.get(GuildPartType.BASE).get(id);
    }

    public Collection<GuildPart> getSymbols() {
        return this.guildParts.get(GuildPartType.SYMBOL).values();
    }

    public GuildPart getSymbol(int id) {
        return this.guildParts.get(GuildPartType.SYMBOL).get(id);
    }

    public Collection<GuildPart> getBaseColors() {
        return this.guildParts.get(GuildPartType.BASE_COLOR).values();
    }

    public GuildPart getBaseColor(int id) {
        return this.guildParts.get(GuildPartType.BASE_COLOR).get(id);
    }

    public Collection<GuildPart> getSymbolColors() {
        return this.guildParts.get(GuildPartType.SYMBOL_COLOR).values();
    }

    public GuildPart getSymbolColor(int id) {
        return this.guildParts.get(GuildPartType.SYMBOL_COLOR).get(id);
    }

    public Collection<GuildPart> getBackgroundColors() {
        return this.guildParts.get(GuildPartType.BACKGROUND_COLOR).values();
    }

    public GuildPart getBackgroundColor(int id) {
        return this.guildParts.get(GuildPartType.BACKGROUND_COLOR).get(id);
    }

    public GuildPart getPart(GuildPartType type, int id) {
        return this.guildParts.get(type).get(id);
    }


    public void setGuild(InteractionGuildFurni furni, int guildId) {
        furni.setGuildId(guildId);
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("UPDATE items SET guild_id = ? WHERE id = ?")) {
            statement.setInt(1, guildId);
            statement.setInt(2, furni.getId());
            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }


    public void dispose() {
        TIntObjectIterator<Guild> guildIterator = this.guilds.iterator();

        for (int i = this.guilds.size(); i-- > 0; ) {
            guildIterator.advance();
            if (guildIterator.value().needsUpdate)
                guildIterator.value().run();

            guildIterator.remove();
        }
        LOGGER.info("Guild Manager -> Disposed!");
    }

    public boolean hasViewedForum(int userId, int guildId) {
        return this.views.stream()
                .anyMatch(v -> v.getUserId() == userId && v.getGuildId() == guildId && v.getTimestamp() > (Emulator.getIntUnixTimestamp() - 7 * 24 * 60 * 60));
    }

    public void addView(int userId, int guildId) {
        ForumView view = new ForumView(userId, guildId, Emulator.getIntUnixTimestamp());

        this.views.add(view);

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("INSERT INTO `guild_forum_views`(`user_id`, `guild_id`, `timestamp`) VALUES (?, ?, ?)")) {
            statement.setInt(1, view.getUserId());
            statement.setInt(2, view.getGuildId());
            statement.setInt(3, view.getTimestamp());

            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public Set<Guild> getMostViewed() {
        return this.views.stream()
                .filter(v -> v.getTimestamp() > (Emulator.getIntUnixTimestamp() - 7 * 24 * 60 * 60))
                .collect(Collectors.groupingBy(ForumView::getGuildId))
                .entrySet()
                .stream()
                .sorted(Comparator.comparingInt((Map.Entry<Integer, List<ForumView>> a) -> a.getValue().size()))
                .map(k -> this.getGuild(k.getKey()))
                .filter(g -> g != null && g.canReadForum() == SettingsState.EVERYONE)
                .limit(100)
                .collect(Collectors.toSet());
    }
}
