package com.eu.habbo.messages.incoming.guilds.forums;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.guilds.Guild;
import com.eu.habbo.habbohotel.guilds.GuildMember;
import com.eu.habbo.habbohotel.guilds.GuildRank;
import com.eu.habbo.habbohotel.guilds.forums.ForumThread;
import com.eu.habbo.habbohotel.guilds.forums.ForumThreadComment;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.guilds.forums.GuildForumAddCommentComposer;
import com.eu.habbo.messages.outgoing.guilds.forums.GuildForumDataComposer;
import com.eu.habbo.messages.outgoing.guilds.forums.GuildForumThreadMessagesComposer;
import com.eu.habbo.messages.outgoing.handshake.ConnectionErrorComposer;
import com.eu.habbo.messages.outgoing.unknown.RoomMessagesPostedCountComposer;

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
                .filter(GuildForumInputGuard.normalize(this.packet.readString()), this.client.getHabbo());
        String message = Emulator.getGameEnvironment()
                .getWordFilter()
                .filter(GuildForumInputGuard.normalize(this.packet.readString()), this.client.getHabbo());

        if (!GuildForumInputGuard.isPositiveId(guildId) || threadId < 0) {
            this.client.sendResponse(new ConnectionErrorComposer(400));
            return;
        }

        Guild guild = Emulator.getGameEnvironment().getGuildManager().getGuild(guildId);

        if (guild == null) {
            this.client.sendResponse(new ConnectionErrorComposer(404));
            return;
        }

        if (message.length() < 10
                || message.length() > 4000
                || (threadId == 0 && (subject.length() < 10 || subject.length() > 120))) {
            this.client.sendResponse(new ConnectionErrorComposer(400));
            return;
        }

        boolean isStaff = this.client.getHabbo().hasPermission(Permission.ACC_MODTOOL_TICKET_Q);

        GuildMember member = Emulator.getGameEnvironment()
                .getGuildManager()
                .getGuildMember(guildId, this.client.getHabbo().getHabboInfo().getId());

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
                                    == this.client.getHabbo().getHabboInfo().getId())
                    || isStaff)) {
                this.client.sendResponse(new ConnectionErrorComposer(403));
                return;
            }

            thread = ForumThread.create(guild, this.client.getHabbo(), subject, message);

            if (thread == null) {
                this.client.sendResponse(new ConnectionErrorComposer(500));
                return;
            }

            this.client.getHabbo().getHabboStats().forumPostsCount += 1;
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
                                == this.client.getHabbo().getHabboInfo().getId())
                || isStaff)) {
            this.client.sendResponse(new ConnectionErrorComposer(403));
            return;
        }

        ForumThreadComment comment = ForumThreadComment.create(thread, this.client.getHabbo(), message);

        if (comment != null) {
            thread.addComment(comment);
            thread.setUpdatedAt(Emulator.getIntUnixTimestamp());
            this.client.getHabbo().getHabboStats().forumPostsCount += 1;
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
                || room.getOwnerId() == this.client.getHabbo().getHabboInfo().getId()) {
            return;
        }

        Habbo owner = Emulator.getGameEnvironment().getHabboManager().getHabbo(room.getOwnerId());

        if (owner != null && owner.getClient() != null) {
            owner.getClient().sendResponse(new RoomMessagesPostedCountComposer(room, messageCount));
        }
    }
}
