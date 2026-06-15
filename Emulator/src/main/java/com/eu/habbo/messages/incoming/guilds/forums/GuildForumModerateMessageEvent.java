package com.eu.habbo.messages.incoming.guilds.forums;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.guilds.Guild;
import com.eu.habbo.habbohotel.guilds.GuildMember;
import com.eu.habbo.habbohotel.guilds.GuildRank;
import com.eu.habbo.habbohotel.guilds.forums.ForumThread;
import com.eu.habbo.habbohotel.guilds.forums.ForumThreadComment;
import com.eu.habbo.habbohotel.guilds.forums.ForumThreadState;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.eu.habbo.messages.outgoing.guilds.forums.PostUpdateMessageComposer;
import com.eu.habbo.messages.outgoing.handshake.ConnectionErrorComposer;


public class GuildForumModerateMessageEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 2000;
    }

    @Override
    public void handle() throws Exception {
        int guildId = packet.readInt();
        int threadId = packet.readInt();
        int messageId = packet.readInt();
        int state = packet.readInt();

        if (!GuildForumInputGuard.isPositiveId(guildId) ||
                !GuildForumInputGuard.isPositiveId(threadId) ||
                !GuildForumInputGuard.isPositiveId(messageId) ||
                !GuildForumInputGuard.isMessageModerationState(state)) {
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

        ForumThreadComment comment = thread.getCommentById(messageId);
        if (comment == null) {
            this.client.sendResponse(new ConnectionErrorComposer(404));
            return;
        }

        boolean hasStaffPermissions = this.client.getHabbo().hasPermission(Permission.ACC_MODTOOL_TICKET_Q);

        GuildMember member = Emulator.getGameEnvironment().getGuildManager().getGuildMember(guildId, this.client.getHabbo().getHabboInfo().getId());
        if (member == null && !hasStaffPermissions) {
            this.client.sendResponse(new ConnectionErrorComposer(401));
            return;
        }

        boolean isGuildAdministrator = (guild.getOwnerId() == this.client.getHabbo().getHabboInfo().getId() || (member != null && member.getRank().equals(GuildRank.ADMIN)));

        if (!isGuildAdministrator && !hasStaffPermissions) {
            this.client.sendResponse(new ConnectionErrorComposer(403));
            return;
        }

        // Restrict state 20 (staff hidden) to staff only
        if (state == 20 && !hasStaffPermissions) {
            this.client.sendResponse(new ConnectionErrorComposer(403));
            return;
        }

        comment.setState(ForumThreadState.fromValue(state));
        comment.setAdminId(this.client.getHabbo().getHabboInfo().getId());
        this.client.sendResponse(new PostUpdateMessageComposer(guild.getId(), thread.getThreadId(), comment));

        Emulator.getThreading().run(comment);

        switch (state) {
            case 10:
            case 20:
                this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FORUMS_MESSAGE_HIDDEN.key).compose());
                break;
            case 1:
                this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FORUMS_MESSAGE_RESTORED.key).compose());
                break;
        }

    }
}
