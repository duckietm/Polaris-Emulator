package polaris.messages.incoming.guilds.forums;

import java.util.ArrayList;
import java.util.List;
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
import polaris.messages.outgoing.guilds.forums.GuildForumCommentsComposer;
import polaris.messages.outgoing.guilds.forums.GuildForumDataComposer;
import polaris.messages.outgoing.handshake.ConnectionErrorComposer;

public class GuildForumThreadsMessagesEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        int guildId = packet.readInt();
        int threadId = packet.readInt();
        int index = packet.readInt(); // 40
        int limit = packet.readInt(); // 20

        if (!GuildForumInputGuard.isPositiveId(guildId)
                || !GuildForumInputGuard.isPositiveId(threadId)
                || !GuildForumInputGuard.isValidPage(index, limit)) {
            this.client.sendResponse(new ConnectionErrorComposer(400));
            return;
        }

        Guild guild = Emulator.getGameEnvironment().getGuildManager().getGuild(guildId);
        ForumThread thread = ForumThread.getById(threadId);
        boolean hasStaffPermissions = this.client.getUser().hasPermission(Permission.ACC_MODTOOL_TICKET_Q);
        if (guild == null || thread == null || !guild.hasForum()) {
            this.client.sendResponse(new ConnectionErrorComposer(404));
            return;
        }

        if (thread.getGuildId() != guildId) {
            this.client.sendResponse(new ConnectionErrorComposer(403));
            return;
        }

        GuildMember member = Emulator.getGameEnvironment()
                .getGuildManager()
                .getGuildMember(guildId, this.client.getUser().getUserInfo().getId());
        boolean isGuildAdministrator =
                (guild.getOwnerId() == this.client.getUser().getUserInfo().getId()
                        || (member != null && member.getRank().equals(GuildRank.ADMIN)));

        if (!guild.canUserReadForum(this.client.getUser().getUserInfo().getId(), member, hasStaffPermissions)) {
            this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FORUMS_ACCESS_DENIED.key).compose());
            return;
        }

        // The check was inverted: a thread a group admin hid was served to everyone, one staff hid to group admins.
        if (ForumThreadState.visibleTo(thread.getState(), hasStaffPermissions, isGuildAdministrator)) {
            List<ForumThreadComment> comments = new ArrayList<>();
            for (ForumThreadComment comment : thread.getComments(limit, index)) {
                comments.add(comment.forReader(hasStaffPermissions, isGuildAdministrator));
            }
            this.client.sendResponse(new GuildForumCommentsComposer(guildId, threadId, index, comments));
            this.client.sendResponse(new GuildForumDataComposer(guild, this.client.getUser()));
        } else {
            this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FORUMS_ACCESS_DENIED.key).compose());
        }
    }
}
