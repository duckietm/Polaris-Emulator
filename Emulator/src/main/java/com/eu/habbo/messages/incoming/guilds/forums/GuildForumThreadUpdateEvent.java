package com.eu.habbo.messages.incoming.guilds.forums;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.guilds.Guild;
import com.eu.habbo.habbohotel.guilds.GuildMember;
import com.eu.habbo.habbohotel.guilds.GuildRank;
import com.eu.habbo.habbohotel.guilds.SettingsState;
import com.eu.habbo.habbohotel.guilds.forums.ForumThread;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.eu.habbo.messages.outgoing.guilds.forums.GuildForumThreadsComposer;
import com.eu.habbo.messages.outgoing.guilds.forums.ThreadUpdatedMessageComposer;
import com.eu.habbo.messages.outgoing.handshake.ConnectionErrorComposer;

public class GuildForumThreadUpdateEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 2000;
    }

    @Override
    public void handle() throws Exception {
        int guildId = this.packet.readInt();
        int threadId = this.packet.readInt();
        boolean isPinned = this.packet.readBoolean();
        boolean isLocked = this.packet.readBoolean();

        if (!GuildForumInputGuard.isPositiveId(guildId) || !GuildForumInputGuard.isPositiveId(threadId)) {
            this.client.sendResponse(new ConnectionErrorComposer(400));
            return;
        }

        Guild guild = Emulator.getGameEnvironment().getGuildManager().getGuild(guildId);
        ForumThread thread = ForumThread.getById(threadId);

        if (guild == null || thread == null) {
            this.client.sendResponse(new ConnectionErrorComposer(404));
            return;
        }

        boolean isStaff = this.client.getHabbo().hasPermission(Permission.ACC_MODTOOL_TICKET_Q);

        GuildMember member = Emulator.getGameEnvironment().getGuildManager().getGuildMember(guildId, this.client.getHabbo().getHabboInfo().getId());
        if (member == null) {
            this.client.sendResponse(new ConnectionErrorComposer(401));
            return;
        }

        boolean isAdmin = (guild.getOwnerId() == this.client.getHabbo().getHabboInfo().getId() || member.getRank().type < GuildRank.MEMBER.type);

        if ((guild.canModForum() == SettingsState.OWNER && guild.getOwnerId() != this.client.getHabbo().getHabboInfo().getId() && !isStaff) || (guild.canModForum() == SettingsState.ADMINS && !isAdmin && !isStaff)) {
            this.client.sendResponse(new ConnectionErrorComposer(403));
            return;
        }

        boolean pinChanged = isPinned != thread.isPinned();
        if (pinChanged) {
            this.client.sendResponse(new BubbleAlertComposer(isPinned ? BubbleAlertKeys.FORUMS_THREAD_PINNED.key : BubbleAlertKeys.FORUMS_THREAD_UNPINNED.key).compose());
        }

        if (isLocked != thread.isLocked()) {
            this.client.sendResponse(new BubbleAlertComposer(isLocked ? BubbleAlertKeys.FORUMS_THREAD_LOCKED.key : BubbleAlertKeys.FORUMS_THREAD_UNLOCKED.key).compose());
        }

        thread.setPinned(isPinned);
        thread.setLocked(isLocked);

        thread.run();


        this.client.sendResponse(new ThreadUpdatedMessageComposer(guild, thread, this.client.getHabbo(), isPinned, isLocked));

        if (pinChanged) {
            this.client.sendResponse(new GuildForumThreadsComposer(guild, 0));
        }
    }
}
