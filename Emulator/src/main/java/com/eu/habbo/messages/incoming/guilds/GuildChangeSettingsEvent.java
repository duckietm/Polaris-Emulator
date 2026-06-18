package com.eu.habbo.messages.incoming.guilds;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.guilds.Guild;
import com.eu.habbo.habbohotel.guilds.GuildMember;
import com.eu.habbo.habbohotel.guilds.GuildState;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.habbohotel.guilds.forums.ForumThread;
import com.eu.habbo.messages.incoming.guilds.forums.GuildForumListEvent;
import com.eu.habbo.messages.outgoing.guilds.GuildInfoComposer;
import com.eu.habbo.messages.outgoing.guilds.forums.GuildForumDataComposer;
import com.eu.habbo.plugin.events.guilds.GuildChangedSettingsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;

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

        Guild guild = Emulator.getGameEnvironment().getGuildManager().getGuild(guildId);

        if (guild != null) {
            if (guild.getOwnerId() == this.client.getHabbo().getHabboInfo().getId() || this.client.getHabbo().hasPermission(Permission.ACC_GUILD_ADMIN)) {
                int state = this.packet.readInt();

                if (state < 0 || state >= GuildState.values().length)
                    return;

                GuildChangedSettingsEvent settingsEvent = new GuildChangedSettingsEvent(guild, state, this.packet.readInt() == 0);
                Emulator.getPluginManager().fireEvent(settingsEvent);

                if (settingsEvent.isCancelled())
                    return;

                if (settingsEvent.state < 0 || settingsEvent.state >= GuildState.values().length)
                    return;

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
                        LOGGER.warn("Forum toggle cooldown for guild {} by user {}", guildId, this.client.getHabbo().getHabboInfo().getUsername());
                    } else {
                        forumToggleCooldown.put(guildId, now);
                        guild.setForum(forumEnabled);

                        if (!forumEnabled) {
                            // Delete all threads and comments for this guild
                            ForumThread.clearCacheForGuild(guildId);
                            deleteForumData(guildId);
                        }

                        // Invalidate caches
                        GuildForumDataComposer.invalidateUnreadCache(guildId);
                        GuildForumListEvent.invalidateActiveForumsCache();
                        GuildForumListEvent.invalidateMyForumsCache(this.client.getHabbo().getHabboInfo().getId());
                    }
                }

                Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(guild.getRoomId());
                if(room != null) {
                    room.refreshGuild(guild);
                }

                guild.needsUpdate = true;

                Emulator.getThreading().run(guild);

                // Send updated group info back to client so hasForum flag refreshes immediately
                GuildMember member = Emulator.getGameEnvironment().getGuildManager().getGuildMember(guild, this.client.getHabbo());
                this.client.sendResponse(new GuildInfoComposer(guild, this.client, false, member));
            }
        }
    }

    private void deleteForumData(int guildId) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            // Delete comments for all threads in this guild
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM `guilds_forums_comments` WHERE `thread_id` IN (SELECT `id` FROM `guilds_forums_threads` WHERE `guild_id` = ?)")) {
                statement.setInt(1, guildId);
                statement.executeUpdate();
            }

            // Delete all threads for this guild
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM `guilds_forums_threads` WHERE `guild_id` = ?")) {
                statement.setInt(1, guildId);
                statement.executeUpdate();
            }

            // Delete forum view records for this guild
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM `guild_forum_views` WHERE `guild_id` = ?")) {
                statement.setInt(1, guildId);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to delete forum data for guild " + guildId, e);
        }
    }
}
