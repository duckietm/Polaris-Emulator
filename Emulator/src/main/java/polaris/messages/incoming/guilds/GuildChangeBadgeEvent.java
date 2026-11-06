package polaris.messages.incoming.guilds;

import polaris.Emulator;
import polaris.core.ConfigurationManager;
import polaris.hotel.guilds.Guild;
import polaris.hotel.guilds.GuildBadgeBuilder;
import polaris.hotel.guilds.GuildBadgeChangeLimiter;
import polaris.hotel.guilds.GuildBadgeInput;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.messages.incoming.MessageHandler;
import polaris.plugin.events.guilds.GuildChangedBadgeEvent;
import polaris.util.imager.badges.BadgeImager;

public class GuildChangeBadgeEvent extends MessageHandler {
    private static final GuildBadgeChangeLimiter LIMITER = new GuildBadgeChangeLimiter();

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        int guildId = this.packet.readInt();

        if (!GuildInputGuard.isPositiveId(guildId)) {
            return;
        }

        Guild guild = Emulator.getGameEnvironment().getGuildManager().getGuild(guildId);
        if (guild != null) {
            if (guild.getOwnerId() == this.client.getUser().getUserInfo().getId()
                    || this.client.getUser().hasPermission(Permission.ACC_GUILD_ADMIN)) {
                Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(guild.getRoomId());

                if (room == null || room.getId() != guild.getRoomId()) return;

                int count = this.packet.readInt();

                String badge = GuildBadgeBuilder.readBadge(this.packet, count);
                if (badge == null) return;

                if (guild.getBadge().equalsIgnoreCase(badge)) return;

                ConfigurationManager config = Emulator.getConfig();
                BadgeImager imager = Emulator.getBadgeImager();

                if (!GuildBadgeInput.isAcceptable(badge, config, imager)) return;

                if (!LIMITER.tryChange(
                        guild.getId(),
                        config.getInt("hotel.guilds.badge.change.cooldown.seconds", 3) * 1000L,
                        System.currentTimeMillis())) return;

                GuildChangedBadgeEvent badgeEvent = new GuildChangedBadgeEvent(guild, badge);
                Emulator.getPluginManager().fireEvent(badgeEvent);

                if (badgeEvent.isCancelled()) return;

                guild.setBadge(badgeEvent.badge);
                guild.needsUpdate = true;

                if (imager != null && config.getBoolean("imager.internal.enabled")) {
                    imager.generate(guild);
                }

                room.refreshGuild(guild);
                room.refreshGuildColors(guild);
                Emulator.getThreading().run(guild);
            }
        }
    }
}
