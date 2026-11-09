package polaris.messages.incoming.guilds.forums;

import polaris.Emulator;
import polaris.hotel.guilds.Guild;
import polaris.hotel.guilds.GuildMember;
import polaris.hotel.guilds.GuildRank;
import polaris.hotel.guilds.SettingsState;
import polaris.hotel.guilds.forums.ForumThread;
import polaris.hotel.permissions.Permission;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.generic.alerts.BubbleAlertComposer;
import polaris.messages.outgoing.generic.alerts.BubbleAlertKeys;
import polaris.messages.outgoing.guilds.forums.GuildForumThreadsComposer;
import polaris.messages.outgoing.guilds.forums.ThreadUpdatedMessageComposer;
import polaris.messages.outgoing.handshake.ConnectionErrorComposer;

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

        // The rights below are this guild's; without this check an admin of one guild pinned and
        // locked the threads of any other, as the sibling moderation handlers already refuse.
        if (thread.getGuildId() != guildId) {
            this.client.sendResponse(new ConnectionErrorComposer(403));
            return;
        }

        boolean isStaff = this.client.getUser().hasPermission(Permission.ACC_MODTOOL_TICKET_Q);

        GuildMember member = Emulator.getGameEnvironment()
                .getGuildManager()
                .getGuildMember(guildId, this.client.getUser().getUserInfo().getId());
        // A request still waiting (or a blocked member) is no member: with moderation open to members, such a user
        // used to pin and lock threads. Staff moderate without being members, so they are not stopped here.
        if (!isAcceptedMember(member) && !isStaff) {
            this.client.sendResponse(new ConnectionErrorComposer(401));
            return;
        }

        boolean isAdmin =
                (guild.getOwnerId() == this.client.getUser().getUserInfo().getId()
                        || (isAcceptedMember(member) && member.getRank().type < GuildRank.MEMBER.type));

        if ((guild.canModForum() == SettingsState.OWNER
                        && guild.getOwnerId()
                                != this.client.getUser().getUserInfo().getId()
                        && !isStaff)
                || (guild.canModForum() == SettingsState.ADMINS && !isAdmin && !isStaff)) {
            this.client.sendResponse(new ConnectionErrorComposer(403));
            return;
        }

        boolean pinChanged = isPinned != thread.isPinned();
        if (pinChanged) {
            this.client.sendResponse(new BubbleAlertComposer(
                            isPinned
                                    ? BubbleAlertKeys.FORUMS_THREAD_PINNED.key
                                    : BubbleAlertKeys.FORUMS_THREAD_UNPINNED.key)
                    .compose());
        }

        if (isLocked != thread.isLocked()) {
            this.client.sendResponse(new BubbleAlertComposer(
                            isLocked
                                    ? BubbleAlertKeys.FORUMS_THREAD_LOCKED.key
                                    : BubbleAlertKeys.FORUMS_THREAD_UNLOCKED.key)
                    .compose());
        }

        thread.setPinned(isPinned);
        thread.setLocked(isLocked);

        thread.run();

        this.client.sendResponse(
                new ThreadUpdatedMessageComposer(guild, thread, this.client.getUser(), isPinned, isLocked));

        if (pinChanged) {
            this.client.sendResponse(new GuildForumThreadsComposer(guild, 0));
        }
    }

    static boolean isAcceptedMember(GuildMember member) {
        return member != null && member.getRank().type <= GuildRank.MEMBER.type;
    }
}
