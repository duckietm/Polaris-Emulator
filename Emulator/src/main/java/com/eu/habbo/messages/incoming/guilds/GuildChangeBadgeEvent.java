package com.eu.habbo.messages.incoming.guilds;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.guilds.Guild;
import com.eu.habbo.habbohotel.guilds.GuildBadgeBuilder;
import com.eu.habbo.habbohotel.guilds.GuildBadgeChangeLimiter;
import com.eu.habbo.habbohotel.guilds.GuildBadgeInput;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.plugin.events.guilds.GuildChangedBadgeEvent;
import com.eu.habbo.util.imager.badges.BadgeImager;

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
            if (guild.getOwnerId() == this.client.getHabbo().getHabboInfo().getId()
                    || this.client.getHabbo().hasPermission(Permission.ACC_GUILD_ADMIN)) {
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
