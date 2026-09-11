package com.eu.habbo.messages.incoming.guilds.forums;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.guilds.forums.GuildForumsUnreadMessagesCountComposer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * How many of my forums have something I have not read. It is the number behind the badge on the
 * forums entry of the me-menu, and the client asks for it on a timer while it is open.
 *
 * <p>A forum counts once, however many new messages it holds: the badge says "these forums moved",
 * not "this many posts appeared".
 */
public class GetUnreadForumsCountEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GetUnreadForumsCountEvent.class);

    private static final String QUERY = "SELECT COUNT(*) FROM (SELECT `guilds`.`id`"
            + " FROM `guilds_members`"
            + " JOIN `guilds` ON `guilds`.`id` = `guilds_members`.`guild_id` AND `guilds`.`forum` = '1'"
            + " JOIN `guilds_forums_threads` ON `guilds_forums_threads`.`guild_id` = `guilds`.`id`"
            + " JOIN `guilds_forums_comments` ON `guilds_forums_comments`.`thread_id` = `guilds_forums_threads`.`id`"
            + " LEFT JOIN `guild_forum_views` ON `guild_forum_views`.`user_id` = `guilds_members`.`user_id`"
            + " AND `guild_forum_views`.`guild_id` = `guilds`.`id`"
            + " WHERE `guilds_members`.`user_id` = ?"
            + " AND `guilds_forums_comments`.`created_at` > COALESCE(`guild_forum_views`.`timestamp`, 0)"
            + " GROUP BY `guilds`.`id`) AS `unread_forums`";

    @Override
    public int getRatelimit() {
        return 5000;
    }

    @Override
    public void handle() throws Exception {
        this.client.sendResponse(new GuildForumsUnreadMessagesCountComposer(
                count(this.client.getHabbo().getHabboInfo().getId())));
    }

    private static int count(int userId) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(QUERY)) {
            statement.setInt(1, userId);

            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) return set.getInt(1);
            }
        } catch (SQLException exception) {
            LOGGER.error("Caught SQL exception", exception);
        }

        return 0;
    }
}
