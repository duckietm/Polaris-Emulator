package polaris.messages.incoming.guilds.forums;

import polaris.Emulator;
import polaris.hotel.guilds.Guild;
import polaris.hotel.guilds.GuildMember;
import polaris.hotel.guilds.GuildRank;
import polaris.hotel.guilds.forums.ForumThread;
import polaris.hotel.guilds.forums.ForumThreadState;
import polaris.hotel.permissions.Permission;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.generic.alerts.BubbleAlertComposer;
import polaris.messages.outgoing.generic.alerts.BubbleAlertKeys;
import polaris.messages.outgoing.guilds.forums.ThreadUpdatedMessageComposer;
import polaris.messages.outgoing.handshake.ConnectionErrorComposer;

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
                .getGuildMember(guildId, this.client.getUser().getUserInfo().getId());
        boolean hasStaffPerms = this.client.getUser().hasPermission(Permission.ACC_MODTOOL_TICKET_Q);

        if (member == null
                && !hasStaffPerms
                && guild.getOwnerId() != this.client.getUser().getUserInfo().getId()) {
            this.client.sendResponse(new ConnectionErrorComposer(401));
            return;
        }

        boolean isGuildAdmin =
                (guild.getOwnerId() == this.client.getUser().getUserInfo().getId()
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
        this.client.sendResponse(updatedThread(guild, thread, this.client.getUser()));
    }

    static ThreadUpdatedMessageComposer updatedThread(Guild guild, ForumThread thread, User moderator) {
        return new ThreadUpdatedMessageComposer(guild, thread, moderator, thread.isPinned(), thread.isLocked());
    }
}
