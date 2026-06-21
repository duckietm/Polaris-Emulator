package com.eu.habbo.messages.incoming.guilds.forums;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.guilds.Guild;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.guilds.forums.GuildForumListComposer;
import com.eu.habbo.messages.outgoing.handshake.ConnectionErrorComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class GuildForumListEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(GuildForumListEvent.class);

    // Cache for active forums list (shared across all users)
    private static volatile Set<Guild> activeForumsCache = null;
    private static volatile long activeForumsCachedAt = 0;
    private static final long ACTIVE_FORUMS_TTL = 30 * 60 * 1000; // 30 minutes

    // Cache for user's forum list
    private static final ConcurrentHashMap<Integer, long[]> myForumsCache = new ConcurrentHashMap<>(); // userId -> {cachedAt}
    private static final ConcurrentHashMap<Integer, Set<Guild>> myForumsData = new ConcurrentHashMap<>();
    private static final long MY_FORUMS_TTL = 10 * 60 * 1000; // 10 minutes

    public static void invalidateActiveForumsCache() {
        activeForumsCache = null;
        activeForumsCachedAt = 0;
    }

    public static void invalidateMyForumsCache(int userId) {
        myForumsCache.remove(userId);
        myForumsData.remove(userId);
    }

    @Override
    public void handle() throws Exception {
        int mode = this.packet.readInt();
        int offset = this.packet.readInt();
        this.packet.readInt();

        Set<Guild> guilds = null;
        switch (mode) {
            case 0: // most active
                guilds = getActiveForums();
                break;

            case 1: // most viewed
                guilds = Emulator.getGameEnvironment().getGuildManager().getMostViewed();
                break;

            case 2: // my groups
                guilds = getMyForums(this.client.getHabbo().getHabboInfo().getId());
                break;
        }

        if (guilds != null) {
            this.client.sendResponse(new GuildForumListComposer(guilds, this.client.getHabbo(), mode, offset));
        }
    }

    private Set<Guild> getActiveForums() {
        long now = System.currentTimeMillis();

        if (activeForumsCache != null && (now - activeForumsCachedAt) < ACTIVE_FORUMS_TTL) {
            return activeForumsCache;
        }

        Set<Guild> guilds = new HashSet<Guild>();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT `guilds`.`id`, SUM(`guilds_forums_threads`.`posts_count`) AS `post_count` " +
                "FROM `guilds_forums_threads` " +
                "LEFT JOIN `guilds` ON `guilds`.`id` = `guilds_forums_threads`.`guild_id` " +
                "WHERE `guilds`.`forum` = '1' AND `guilds_forums_threads`.`created_at` > ? " +
                "GROUP BY `guilds`.`id` " +
                "ORDER BY `post_count` DESC LIMIT 100")) {
            statement.setInt(1, Emulator.getIntUnixTimestamp() - 7 * 24 * 60 * 60);
            ResultSet set = statement.executeQuery();

            while (set.next()) {
                Guild guild = Emulator.getGameEnvironment().getGuildManager().getGuild(set.getInt("id"));

                if (guild != null) {
                    guilds.add(guild);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
            this.client.sendResponse(new ConnectionErrorComposer(500));
        }

        activeForumsCache = guilds;
        activeForumsCachedAt = now;

        return guilds;
    }

    private Set<Guild> getMyForums(int userId) {
        long now = System.currentTimeMillis();

        long[] cached = myForumsCache.get(userId);
        if (cached != null && (now - cached[0]) < MY_FORUMS_TTL) {
            Set<Guild> data = myForumsData.get(userId);
            if (data != null) return data;
        }

        Set<Guild> guilds = new HashSet<Guild>();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT `guilds`.`id` FROM `guilds_members` " +
                "LEFT JOIN `guilds` ON `guilds`.`id` = `guilds_members`.`guild_id` " +
                "WHERE `guilds_members`.`user_id` = ? AND `guilds`.`forum` = '1'")) {
            statement.setInt(1, userId);
            ResultSet set = statement.executeQuery();

            while (set.next()) {
                Guild guild = Emulator.getGameEnvironment().getGuildManager().getGuild(set.getInt("id"));

                if (guild != null) {
                    guilds.add(guild);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
            this.client.sendResponse(new ConnectionErrorComposer(500));
        }

        myForumsCache.put(userId, new long[]{now});
        myForumsData.put(userId, guilds);

        return guilds;
    }
}
