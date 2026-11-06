package polaris.messages.incoming.guilds.forums;

import polaris.Emulator;
import polaris.hotel.guilds.Guild;
import polaris.hotel.guilds.GuildMember;
import polaris.hotel.guilds.GuildRank;
import polaris.hotel.guilds.forums.ForumThread;
import polaris.hotel.guilds.forums.ForumThreadComment;
import polaris.hotel.guilds.forums.ForumThreadState;
import polaris.hotel.permissions.Permission;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.generic.alerts.BubbleAlertComposer;
import polaris.messages.outgoing.generic.alerts.BubbleAlertKeys;
import polaris.messages.outgoing.guilds.forums.PostUpdateMessageComposer;
import polaris.messages.outgoing.handshake.ConnectionErrorComposer;

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

        if (!GuildForumInputGuard.isPositiveId(guildId)
                || !GuildForumInputGuard.isPositiveId(threadId)
                || !GuildForumInputGuard.isPositiveId(messageId)
                || !GuildForumInputGuard.isMessageModerationState(state)) {
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

        boolean hasStaffPermissions = this.client.getUser().hasPermission(Permission.ACC_MODTOOL_TICKET_Q);

        GuildMember member = Emulator.getGameEnvironment()
                .getGuildManager()
                .getGuildMember(guildId, this.client.getUser().getUserInfo().getId());
        if (member == null && !hasStaffPermissions) {
            this.client.sendResponse(new ConnectionErrorComposer(401));
            return;
        }

        boolean isGuildAdministrator =
                (guild.getOwnerId() == this.client.getUser().getUserInfo().getId()
                        || (member != null && member.getRank().equals(GuildRank.ADMIN)));

        if (!isGuildAdministrator && !hasStaffPermissions) {
            this.client.sendResponse(new ConnectionErrorComposer(403));
            return;
        }

        // Official: state 20 is hidden by staff. Only staff may set it, and only staff may change a
        // message staff hid (a group admin can't restore it).
        if ((state == 20 || comment.getState() == ForumThreadState.HIDDEN_BY_GUILD_ADMIN) && !hasStaffPermissions) {
            this.client.sendResponse(new ConnectionErrorComposer(403));
            return;
        }

        comment.setState(ForumThreadState.fromValue(state));
        comment.setAdminId(this.client.getUser().getUserInfo().getId());
        this.client.sendResponse(new PostUpdateMessageComposer(guild.getId(), thread.getThreadId(), comment));

        Emulator.getThreading().run(comment);

        switch (state) {
            case 10:
            case 20:
                this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FORUMS_MESSAGE_HIDDEN.key).compose());
                break;
            case 1:
                this.client.sendResponse(
                        new BubbleAlertComposer(BubbleAlertKeys.FORUMS_MESSAGE_RESTORED.key).compose());
                break;
        }
    }
}
