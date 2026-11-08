package polaris.messages.incoming.guilds;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.guilds.Guild;
import polaris.hotel.guilds.GuildMember;
import polaris.hotel.guilds.GuildState;
import polaris.hotel.guilds.forums.ForumThread;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.incoming.guilds.forums.GuildForumListEvent;
import polaris.messages.outgoing.guilds.GuildInfoComposer;
import polaris.messages.outgoing.guilds.forums.GuildForumDataComposer;
import polaris.plugin.events.guilds.GuildChangedSettingsEvent;

public class GuildChangeSettingsEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GuildChangeSettingsEvent.class);

    // Cooldown for forum toggle per guild: guildId -> last toggle timestamp
    private static final ConcurrentHashMap<Integer, Long> forumToggleCooldown = new ConcurrentHashMap<>();
    private static final long FORUM_TOGGLE_COOLDOWN_MS = 30_000; // 30 seconds

    @Override
    public int getRatelimit() {
        return 2000; // 2 seconds between settings saves
    }

    @Override
    public void handle() throws Exception {
        int guildId = this.packet.readInt();

        if (!GuildInputGuard.isPositiveId(guildId)) {
            return;
        }

        Guild guild = Emulator.getGameEnvironment().getGuildManager().getGuild(guildId);

        if (guild != null) {
            if (guild.getOwnerId() == this.client.getUser().getUserInfo().getId()
                    || this.client.getUser().hasPermission(Permission.ACC_GUILD_ADMIN)) {
                int state = this.packet.readInt();

                if (state < 0 || state >= GuildState.values().length) return;

                GuildChangedSettingsEvent settingsEvent =
                        new GuildChangedSettingsEvent(guild, state, this.packet.readInt() == 0);
                Emulator.getPluginManager().fireEvent(settingsEvent);

                if (settingsEvent.isCancelled()) return;

                if (settingsEvent.state < 0 || settingsEvent.state >= GuildState.values().length) return;

                guild.setState(GuildState.valueOf(settingsEvent.state));
                guild.setRights(settingsEvent.rights);

                // Read forum toggle
                boolean forumEnabled = this.packet.readBoolean();
                boolean wasForumEnabled = guild.hasForum();

                if (forumEnabled != wasForumEnabled) {
                    // Enforce cooldown on forum toggle to prevent rapid enable/disable spam
                    Long lastToggle = forumToggleCooldown.get(guildId);
                    long now = System.currentTimeMillis();

                    if (lastToggle != null && (now - lastToggle) < FORUM_TOGGLE_COOLDOWN_MS) {
                        LOGGER.warn(
                                "Forum toggle cooldown for guild {} by user {}",
                                guildId,
                                this.client.getUser().getUserInfo().getUsername());
                    } else {
                        forumToggleCooldown.put(guildId, now);

                        if (forumEnabled) {
                            guild.setForum(true);
                        } else if (this.deleteForumData(guildId)) {
                            // The forum is switched off and its cache dropped only once its threads, comments and
                            // views are gone together: a failed delete used to leave a disabled forum with half
                            // its data and an empty cache.
                            guild.setForum(false);
                            ForumThread.clearCacheForGuild(guildId);
                        }

                        // Invalidate caches
                        GuildForumDataComposer.invalidateUnreadCache(guildId);
                        GuildForumListEvent.invalidateActiveForumsCache();
                        GuildForumListEvent.invalidateMyForumsCache(
                                this.client.getUser().getUserInfo().getId());
                    }
                }

                Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(guild.getRoomId());
                if (room != null) {
                    room.refreshGuild(guild);
                }

                guild.needsUpdate = true;

                Emulator.getThreading().run(guild);

                // Send updated group info back to client so hasForum flag refreshes immediately
                GuildMember member =
                        Emulator.getGameEnvironment().getGuildManager().getGuildMember(guild, this.client.getUser());
                this.client.sendResponse(new GuildInfoComposer(guild, this.client, false, member));
            }
        }
    }

    private boolean deleteForumData(int guildId) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            return deleteForumData(connection, guildId);
        } catch (SQLException e) {
            LOGGER.error("Failed to delete forum data for guild " + guildId, e);
            return false;
        }
    }

    /** Deletes the comments, threads and views of a guild's forum in one transaction; false when rolled back. */
    static boolean deleteForumData(Connection connection, int guildId) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM `guilds_forums_comments` WHERE `thread_id` IN (SELECT `id` FROM `guilds_forums_threads` WHERE `guild_id` = ?)")) {
                statement.setInt(1, guildId);
                statement.executeUpdate();
            }

            try (PreparedStatement statement =
                    connection.prepareStatement("DELETE FROM `guilds_forums_threads` WHERE `guild_id` = ?")) {
                statement.setInt(1, guildId);
                statement.executeUpdate();
            }

            try (PreparedStatement statement =
                    connection.prepareStatement("DELETE FROM `guild_forum_views` WHERE `guild_id` = ?")) {
                statement.setInt(1, guildId);
                statement.executeUpdate();
            }

            connection.commit();
            return true;
        } catch (SQLException e) {
            connection.rollback();
            LOGGER.error("Failed to delete forum data for guild " + guildId, e);
            return false;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }
}
