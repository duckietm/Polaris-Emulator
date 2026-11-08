package polaris.messages.incoming.guilds.forums;

import polaris.Emulator;
import polaris.hotel.guilds.Guild;
import polaris.hotel.guilds.GuildMember;
import polaris.hotel.guilds.GuildRank;
import polaris.hotel.guilds.forums.ForumThread;
import polaris.hotel.guilds.forums.ForumThreadComment;
import polaris.hotel.guilds.forums.ForumThreadState;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.guilds.forums.GuildForumAddCommentComposer;
import polaris.messages.outgoing.guilds.forums.GuildForumDataComposer;
import polaris.messages.outgoing.guilds.forums.GuildForumThreadMessagesComposer;
import polaris.messages.outgoing.handshake.ConnectionErrorComposer;
import polaris.messages.outgoing.rooms.RoomMessageNotificationComposer;

public class GuildForumPostThreadEvent extends MessageHandler {

    @Override
    public int getRatelimit() {
        return 2000;
    }

    @Override
    public void handle() throws Exception {
        int guildId = this.packet.readInt();
        int threadId = this.packet.readInt();
        String subject = Emulator.getGameEnvironment()
                .getWordFilter()
                .filter(GuildForumInputGuard.normalize(this.packet.readString()), this.client.getUser());
        String message = Emulator.getGameEnvironment()
                .getWordFilter()
                .filter(GuildForumInputGuard.normalize(this.packet.readString()), this.client.getUser());

        if (!GuildForumInputGuard.isPositiveId(guildId) || threadId < 0) {
            this.client.sendResponse(new ConnectionErrorComposer(400));
            return;
        }

        Guild guild = Emulator.getGameEnvironment().getGuildManager().getGuild(guildId);

        if (guild == null) {
            this.client.sendResponse(new ConnectionErrorComposer(404));
            return;
        }

        if (!validLengths(message, subject, threadId == 0)) {
            this.client.sendResponse(new ConnectionErrorComposer(400));
            return;
        }

        boolean isStaff = this.client.getUser().hasPermission(Permission.ACC_MODTOOL_TICKET_Q);

        GuildMember member = Emulator.getGameEnvironment()
                .getGuildManager()
                .getGuildMember(guildId, this.client.getUser().getUserInfo().getId());

        // Posting needs the same forum the reader sees: a forum switched off, or one only members may read,
        // used to take posts from anyone who knew the guild id.
        int userId = this.client.getUser().getUserInfo().getId();
        if (!guild.hasForum() || !guild.canUserReadForum(userId, member, isStaff)) {
            this.client.sendResponse(new ConnectionErrorComposer(403));
            return;
        }

        ForumThread thread = ForumThread.getById(threadId);

        if (threadId == 0) {
            if (!((guild.canPostThreads().state == 0)
                    || (guild.canPostThreads().state == 1
                            && member != null
                            && member.getRank().type <= GuildRank.MEMBER.type)
                    || (guild.canPostThreads().state == 2
                            && member != null
                            && (member.getRank().type < GuildRank.MEMBER.type))
                    || (guild.canPostThreads().state == 3
                            && guild.getOwnerId()
                                    == this.client.getUser().getUserInfo().getId())
                    || isStaff)) {
                this.client.sendResponse(new ConnectionErrorComposer(403));
                return;
            }

            thread = ForumThread.create(guild, this.client.getUser(), subject, message);

            if (thread == null) {
                this.client.sendResponse(new ConnectionErrorComposer(500));
                return;
            }

            this.client.getUser().getUserStats().forumPostsCount += 1;
            thread.setPostsCount(thread.getPostsCount() + 1);
            GuildForumDataComposer.invalidateUnreadCache(guildId);
            this.client.sendResponse(new GuildForumThreadMessagesComposer(thread));
            notifyRoomOwner(guild, 1);
            return;
        }

        if (thread == null) {
            this.client.sendResponse(new ConnectionErrorComposer(404));
            return;
        }

        if (thread.getGuildId() != guildId) {
            this.client.sendResponse(new ConnectionErrorComposer(403));
            return;
        }

        boolean isGuildAdministrator = guild.getOwnerId() == userId
                || (member != null && member.getRank().equals(GuildRank.ADMIN));
        if (!ForumThreadState.visibleTo(thread.getState(), isStaff, isGuildAdministrator)) {
            this.client.sendResponse(new ConnectionErrorComposer(403));
            return;
        }

        if (thread.isLocked() && !isStaff) {
            this.client.sendResponse(new ConnectionErrorComposer(403));
            return;
        }

        if (!((guild.canPostMessages().state == 0)
                || (guild.canPostMessages().state == 1
                        && member != null
                        && member.getRank().type <= GuildRank.MEMBER.type)
                || (guild.canPostMessages().state == 2
                        && member != null
                        && (member.getRank().type < GuildRank.MEMBER.type))
                || (guild.canPostMessages().state == 3
                        && guild.getOwnerId()
                                == this.client.getUser().getUserInfo().getId())
                || isStaff)) {
            this.client.sendResponse(new ConnectionErrorComposer(403));
            return;
        }

        ForumThreadComment comment = ForumThreadComment.create(thread, this.client.getUser(), message);

        if (comment != null) {
            thread.addComment(comment);
            thread.setUpdatedAt(Emulator.getIntUnixTimestamp());
            this.client.getUser().getUserStats().forumPostsCount += 1;
            thread.setPostsCount(thread.getPostsCount() + 1);
            GuildForumDataComposer.invalidateUnreadCache(guildId);
            this.client.sendResponse(new GuildForumAddCommentComposer(comment));
            notifyRoomOwner(guild, 1);
        } else {
            this.client.sendResponse(new ConnectionErrorComposer(500));
        }
    }

    /**
     * Official {@code RoomMessagesPostedCount} (1634) -> the `roommessagesposted` bubble
     * ("%messages_count% new messages were posted in %room_name%"). The forum of a group
     * belongs to the group's room, so the room owner is the one notified.
     */
    private void notifyRoomOwner(Guild guild, int messageCount) {
        if (guild.getRoomId() <= 0) {
            return;
        }

        Room room = Emulator.getGameEnvironment().getRoomManager().loadRoom(guild.getRoomId());

        if (room == null
                || room.getOwnerId() == this.client.getUser().getUserInfo().getId()) {
            return;
        }

        User owner = Emulator.getGameEnvironment().getUserManager().getUser(room.getOwnerId());

        if (owner != null && owner.getClient() != null) {
            owner.getClient().sendResponse(new RoomMessageNotificationComposer(room, messageCount));
        }
    }

    /**
     * The official editor refuses a message (and a new thread's subject) of 10 characters or fewer
     * (ComposeMessageView: length &lt;= 10), so at least 11 are needed.
     */
    static boolean validLengths(String message, String subject, boolean newThread) {
        if (message.length() <= 10 || message.length() > 4000) return false;
        return !newThread || (subject.length() > 10 && subject.length() <= 120);
    }
}
