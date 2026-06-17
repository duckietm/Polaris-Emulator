package com.eu.habbo.messages.incoming.guilds.forums;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.guilds.Guild;
import com.eu.habbo.habbohotel.guilds.GuildMember;
import com.eu.habbo.habbohotel.guilds.GuildRank;
import com.eu.habbo.habbohotel.guilds.forums.ForumThread;
import com.eu.habbo.habbohotel.guilds.forums.ForumThreadState;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.eu.habbo.messages.outgoing.guilds.forums.GuildForumDataComposer;
import com.eu.habbo.messages.outgoing.guilds.forums.GuildForumThreadMessagesComposer;
import com.eu.habbo.messages.outgoing.guilds.forums.GuildForumThreadsComposer;
import com.eu.habbo.messages.outgoing.handshake.ConnectionErrorComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;


public class GuildForumModerateThreadEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GuildForumModerateThreadEvent.class);

    @Override
    public int getRatelimit() {
        return 2000;
    }

    @Override
    public void handle() throws Exception {
        int guildId = packet.readInt();
        int threadId = packet.readInt();
        int state = packet.readInt();

        if (!GuildForumInputGuard.isPositiveId(guildId) ||
                !GuildForumInputGuard.isPositiveId(threadId) ||
                !GuildForumInputGuard.isThreadModerationState(state)) {
            this.client.sendResponse(new ConnectionErrorComposer(400));
            return;
        }

        Guild guild = Emulator.getGameEnvironment().getGuildManager().getGuild(guildId);
        ForumThread thread = ForumThread.getById(threadId);

        if (guild == null || thread == null) {
            this.client.sendResponse(new ConnectionErrorComposer(404));
            return;
        }

        if (thread.getGuildId() != guildId) {
            this.client.sendResponse(new ConnectionErrorComposer(403));
            return;
        }

        GuildMember member = Emulator.getGameEnvironment().getGuildManager().getGuildMember(guildId, this.client.getHabbo().getHabboInfo().getId());
        boolean hasStaffPerms = this.client.getHabbo().hasPermission(Permission.ACC_MODTOOL_TICKET_Q);

        if (member == null && !hasStaffPerms && guild.getOwnerId() != this.client.getHabbo().getHabboInfo().getId()) {
            this.client.sendResponse(new ConnectionErrorComposer(401));
            return;
        }

        boolean isGuildAdmin = (guild.getOwnerId() == this.client.getHabbo().getHabboInfo().getId() || (member != null && member.getRank().equals(GuildRank.ADMIN)));

        if (!isGuildAdmin && !hasStaffPerms) {
            this.client.sendResponse(new ConnectionErrorComposer(403));
            return;
        }

        // State 20 = permanent delete (thread + comments removed from DB)
        if (state == 20) {
            deleteThread(threadId);
            ForumThread.clearCacheForGuild(guildId);
            GuildForumDataComposer.invalidateUnreadCache(guildId);

            this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FORUMS_THREAD_HIDDEN.key).compose());
            this.client.sendResponse(new GuildForumThreadsComposer(guild, 0));
            return;
        }

        thread.setState(ForumThreadState.fromValue(state));
        thread.run();

        switch (state) {
            case 10:
                this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FORUMS_THREAD_HIDDEN.key).compose());
                break;
            case 1:
                this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FORUMS_THREAD_RESTORED.key).compose());
                break;
        }

        this.client.sendResponse(new GuildForumThreadMessagesComposer(thread));
        this.client.sendResponse(new GuildForumThreadsComposer(guild, 0));
    }

    private void deleteThread(int threadId) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM `guilds_forums_comments` WHERE `thread_id` = ?")) {
                statement.setInt(1, threadId);
                statement.executeUpdate();
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM `guilds_forums_threads` WHERE `id` = ?")) {
                statement.setInt(1, threadId);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to delete thread " + threadId, e);
        }
    }
}
