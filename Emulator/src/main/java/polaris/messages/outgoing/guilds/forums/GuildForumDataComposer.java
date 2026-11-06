package polaris.messages.outgoing.guilds.forums;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.guilds.Guild;
import polaris.hotel.guilds.GuildMember;
import polaris.hotel.guilds.GuildRank;
import polaris.hotel.guilds.forums.ForumThread;
import polaris.hotel.guilds.forums.ForumThreadComment;
import polaris.hotel.permissions.Permission;
import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;
import polaris.messages.outgoing.handshake.ConnectionErrorComposer;

public class GuildForumDataComposer extends MessageComposer {
    private static final Logger LOGGER = LoggerFactory.getLogger(GuildForumDataComposer.class);

    // Cache for user last-seen timestamps: key = "userId:guildId", value = {timestamp, cachedAt}
    private static final ConcurrentHashMap<String, long[]> lastSeenCache = new ConcurrentHashMap<>();
    private static final long LAST_SEEN_CACHE_TTL = 5 * 60 * 1000; // 5 minutes

    // Cache for unread counts: key = "guildId:lastSeenAt", value = {count, cachedAt}
    private static final ConcurrentHashMap<String, long[]> unreadCache = new ConcurrentHashMap<>();
    private static final long UNREAD_CACHE_TTL = 2 * 60 * 1000; // 2 minutes

    public final Guild guild;
    public User user;

    public GuildForumDataComposer(Guild guild, User user) {
        this.guild = guild;
        this.user = user;
    }

    public static void invalidateLastSeenCache(int userId, int guildId) {
        lastSeenCache.remove(userId + ":" + guildId);
    }

    public static void invalidateUnreadCache(int guildId) {
        unreadCache.entrySet().removeIf(entry -> entry.getKey().startsWith(guildId + ":"));
    }

    private static int getLastSeenAt(int userId, int guildId) {
        String key = userId + ":" + guildId;
        long now = System.currentTimeMillis();

        long[] cached = lastSeenCache.get(key);
        if (cached != null && (now - cached[1]) < LAST_SEEN_CACHE_TTL) {
            return (int) cached[0];
        }

        int lastSeenAt = 0;
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT `timestamp` FROM `guild_forum_views` WHERE `user_id` = ? AND `guild_id` = ? LIMIT 1")) {
            statement.setInt(1, userId);
            statement.setInt(2, guildId);
            ResultSet set = statement.executeQuery();
            if (set.next()) {
                lastSeenAt = set.getInt("timestamp");
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        lastSeenCache.put(key, new long[] {lastSeenAt, now});
        return lastSeenAt;
    }

    private static int getUnreadCount(int guildId, int lastSeenAt) {
        String key = guildId + ":" + lastSeenAt;
        long now = System.currentTimeMillis();

        long[] cached = unreadCache.get(key);
        if (cached != null && (now - cached[1]) < UNREAD_CACHE_TTL) {
            return (int) cached[0];
        }

        int newComments = 0;
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM `guilds_forums_comments` "
                                + "JOIN `guilds_forums_threads` ON `guilds_forums_threads`.`id` = `guilds_forums_comments`.`thread_id` "
                                + "WHERE `guilds_forums_threads`.`guild_id` = ? AND `guilds_forums_comments`.`created_at` > ?")) {
            statement.setInt(1, guildId);
            statement.setInt(2, lastSeenAt);

            ResultSet set = statement.executeQuery();
            if (set.next()) {
                newComments = set.getInt(1);
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        unreadCache.put(key, new long[] {newComments, now});
        return newComments;
    }

    public static void serializeForumData(ServerMessage response, Guild guild, User user) {

        final Set<ForumThread> forumThreads = ForumThread.getByGuildId(guild.getId());
        int lastSeenAt = getLastSeenAt(user.getUserInfo().getId(), guild.getId());

        int totalComments = 0;
        int totalThreads = 0;
        ForumThreadComment lastComment = null;

        synchronized (forumThreads) {
            for (ForumThread thread : forumThreads) {
                totalThreads++;
                totalComments += thread.getPostsCount();

                ForumThreadComment comment = thread.getLastComment();
                if (comment != null && (lastComment == null || lastComment.getCreatedAt() < comment.getCreatedAt())) {
                    lastComment = comment;
                }
            }
        }

        int newComments = getUnreadCount(guild.getId(), lastSeenAt);

        response.appendInt(guild.getId());

        response.appendString(guild.getName());
        response.appendString(guild.getDescription());
        response.appendString(guild.getBadge());

        response.appendInt(totalThreads);
        response.appendInt(0); // Rating

        response.appendInt(totalComments); // Total comments
        response.appendInt(newComments); // Unread comments

        response.appendInt(lastComment != null ? lastComment.getThreadId() : -1);
        response.appendInt(lastComment != null ? lastComment.getUserId() : -1);
        response.appendString(
                lastComment != null && lastComment.getUser() != null
                        ? lastComment.getUser().getUserInfo().getUsername()
                        : "");
        response.appendInt(lastComment != null ? Emulator.getIntUnixTimestamp() - lastComment.getCreatedAt() : 0);
    }

    @Override
    protected ServerMessage composeInternal() {

        try {
            this.response.init(Outgoing.GuildForumDataComposer);
            serializeForumData(this.response, guild, user);

            GuildMember member = Emulator.getGameEnvironment().getGuildManager().getGuildMember(guild, user);
            boolean isAdmin = member != null
                    && (member.getRank().type < GuildRank.MEMBER.type
                            || guild.getOwnerId() == this.user.getUserInfo().getId());
            boolean isStaff = this.user.hasPermission(Permission.ACC_MODTOOL_TICKET_Q);

            String errorRead = "";
            String errorPost = "";
            String errorStartThread = "";
            String errorModerate = "";

            if (guild.canReadForum().state == 1 && member == null && !isStaff) {
                errorRead = "not_member";
            } else if (guild.canReadForum().state == 2 && !isAdmin && !isStaff) {
                errorRead = "not_admin";
            }

            if (guild.canPostMessages().state == 1 && member == null && !isStaff) {
                errorPost = "not_member";
            } else if (guild.canPostMessages().state == 2 && !isAdmin && !isStaff) {
                errorPost = "not_admin";
            } else if (guild.canPostMessages().state == 3
                    && guild.getOwnerId() != this.user.getUserInfo().getId()
                    && !isStaff) {
                errorPost = "not_owner";
            }

            if (guild.canPostThreads().state == 1 && member == null && !isStaff) {
                errorStartThread = "not_member";
            } else if (guild.canPostThreads().state == 2 && !isAdmin && !isStaff) {
                errorStartThread = "not_admin";
            } else if (guild.canPostThreads().state == 3
                    && guild.getOwnerId() != this.user.getUserInfo().getId()
                    && !isStaff) {
                errorStartThread = "not_owner";
            }

            if (guild.canModForum().state == 3
                    && guild.getOwnerId() != this.user.getUserInfo().getId()
                    && !isStaff) {
                errorModerate = "not_owner";
            } else if (!isAdmin && !isStaff) {
                errorModerate = "not_admin";
            }

            this.response.appendInt(guild.canReadForum().state);
            this.response.appendInt(guild.canPostMessages().state);
            this.response.appendInt(guild.canPostThreads().state);
            this.response.appendInt(guild.canModForum().state);
            this.response.appendString(errorRead);
            this.response.appendString(errorPost);
            this.response.appendString(errorStartThread);
            this.response.appendString(errorModerate);
            this.response.appendString(""); // citizen
            this.response.appendBoolean(
                    guild.getOwnerId() == this.user.getUserInfo().getId()); // Forum Settings
            if (guild.canModForum().state == 3) {
                this.response.appendBoolean(
                        guild.getOwnerId() == this.user.getUserInfo().getId() || isStaff);
            } else {
                this.response.appendBoolean(
                        guild.getOwnerId() == this.user.getUserInfo().getId() || isStaff || isAdmin); // Can Mod (staff)
            }
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
            return new ConnectionErrorComposer(500).compose();
        }

        return this.response;
    }

    public Guild getGuild() {
        return guild;
    }

    public User getUser() {
        return user;
    }
}
