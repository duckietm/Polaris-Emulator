package polaris.messages.incoming.guilds.forums;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.guilds.Guild;
import polaris.hotel.guilds.GuildMember;
import polaris.hotel.permissions.Permission;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.guilds.forums.GuildForumDataComposer;

public class GuildForumMarkAsReadEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GuildForumMarkAsReadEvent.class);

    @Override
    public int getRatelimit() {
        return 2000;
    }

    @Override
    public void handle() throws Exception {
        int count = this.packet.readInt();
        int userId = this.client.getUser().getUserInfo().getId();
        int timestamp = Emulator.getIntUnixTimestamp();

        if (!GuildForumInputGuard.isValidMarkReadBatch(count)) {
            return;
        }

        for (int i = 0; i < count; i++) {
            int guildId = this.packet.readInt();
            this.packet.readInt(); // messageId (not used, we track by timestamp)
            this.packet.readBoolean(); // isRead

            if (!GuildForumInputGuard.isPositiveId(guildId)) {
                continue;
            }

            Guild guild = Emulator.getGameEnvironment().getGuildManager().getGuild(guildId);
            if (guild == null || !guild.hasForum()) {
                continue;
            }

            GuildMember member = Emulator.getGameEnvironment().getGuildManager().getGuildMember(guildId, userId);
            boolean staff = this.client.getUser().hasPermission(Permission.ACC_MODTOOL_TICKET_Q);
            if (!guild.canUserReadForum(userId, member, staff)) {
                continue;
            }

            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO `guild_forum_views` (`user_id`, `guild_id`, `timestamp`) VALUES (?, ?, ?) "
                                    + "ON DUPLICATE KEY UPDATE `timestamp` = ?")) {
                statement.setInt(1, userId);
                statement.setInt(2, guildId);
                statement.setInt(3, timestamp);
                statement.setInt(4, timestamp);
                statement.executeUpdate();
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }

            // Invalidate caches so next request gets fresh data
            GuildForumDataComposer.invalidateLastSeenCache(userId, guildId);
            GuildForumDataComposer.invalidateUnreadCache(guildId);
        }
    }
}
