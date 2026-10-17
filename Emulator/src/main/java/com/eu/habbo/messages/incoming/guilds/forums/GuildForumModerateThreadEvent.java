package com.eu.habbo.messages.incoming.guilds.forums;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.guilds.Guild;
import com.eu.habbo.habbohotel.guilds.GuildMember;
import com.eu.habbo.habbohotel.guilds.GuildRank;
import com.eu.habbo.habbohotel.guilds.forums.ForumThread;
import com.eu.habbo.habbohotel.guilds.forums.ForumThreadState;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.eu.habbo.messages.outgoing.guilds.forums.ThreadUpdatedMessageComposer;
import com.eu.habbo.messages.outgoing.handshake.ConnectionErrorComposer;

public class GuildForumModerateThreadEvent extends MessageHandler {

    @Override
    public int getRatelimit() {
        return 2000;
    }

    @Override
    public void handle() throws Exception {
        int guildId = packet.readInt();
        int threadId = packet.readInt();
        int state = packet.readInt();

        if (!GuildForumInputGuard.isPositiveId(guildId)
                || !GuildForumInputGuard.isPositiveId(threadId)
                || !GuildForumInputGuard.isThreadModerationState(state)) {
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

        GuildMember member = Emulator.getGameEnvironment()
                .getGuildManager()
                .getGuildMember(guildId, this.client.getHabbo().getHabboInfo().getId());
        boolean hasStaffPerms = this.client.getHabbo().hasPermission(Permission.ACC_MODTOOL_TICKET_Q);

        if (member == null
                && !hasStaffPerms
                && guild.getOwnerId() != this.client.getHabbo().getHabboInfo().getId()) {
            this.client.sendResponse(new ConnectionErrorComposer(401));
            return;
        }

        boolean isGuildAdmin =
                (guild.getOwnerId() == this.client.getHabbo().getHabboInfo().getId()
                        || (member != null && member.getRank().equals(GuildRank.ADMIN)));

        if (!isGuildAdmin && !hasStaffPerms) {
            this.client.sendResponse(new ConnectionErrorComposer(403));
            return;
        }

        // Official (GroupForumController): a group admin hides a thread with state 10, staff with
        // state 20; both are soft states that can be undone. State 20 used to delete the thread and
        // all its posts from the database, and any group admin could send it.
        // Only staff may set state 20, or change a thread staff hid.
        if ((state == 20 || thread.getState() == ForumThreadState.HIDDEN_BY_GUILD_ADMIN) && !hasStaffPerms) {
            this.client.sendResponse(new ConnectionErrorComposer(403));
            return;
        }

        thread.setState(ForumThreadState.fromValue(state));
        thread.run();

        switch (state) {
            case 10:
            case 20:
                this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FORUMS_THREAD_HIDDEN.key).compose());
                break;
            case 1:
                this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FORUMS_THREAD_RESTORED.key).compose());
                break;
        }

        // Official: the moderator gets the one updated thread (ThreadUpdated) instead of every list again.
        this.client.sendResponse(updatedThread(guild, thread, this.client.getHabbo()));
    }

    static ThreadUpdatedMessageComposer updatedThread(Guild guild, ForumThread thread, Habbo moderator) {
        return new ThreadUpdatedMessageComposer(guild, thread, moderator, thread.isPinned(), thread.isLocked());
    }
}
