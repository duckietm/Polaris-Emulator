package polaris.messages.incoming.guilds;

import polaris.Emulator;
import polaris.hotel.guilds.Guild;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.messages.incoming.MessageHandler;
import polaris.plugin.events.guilds.GuildChangedColorsEvent;

public class GuildChangeColorsEvent extends MessageHandler {
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
                int colorOne = this.packet.readInt();
                int colorTwo = this.packet.readInt();

                if (!Emulator.getGameEnvironment().getGuildManager().symbolColor(colorOne)
                        || !Emulator.getGameEnvironment().getGuildManager().backgroundColor(colorTwo)) return;

                GuildChangedColorsEvent colorsEvent = new GuildChangedColorsEvent(guild, colorOne, colorTwo);
                Emulator.getPluginManager().fireEvent(colorsEvent);

                if (colorsEvent.isCancelled()) return;

                if (!Emulator.getGameEnvironment().getGuildManager().symbolColor(colorsEvent.colorOne)
                        || !Emulator.getGameEnvironment().getGuildManager().backgroundColor(colorsEvent.colorTwo))
                    return;

                if (guild.getColorOne() != colorsEvent.colorOne || guild.getColorTwo() != colorsEvent.colorTwo) {
                    guild.setColorOne(colorsEvent.colorOne);
                    guild.setColorTwo(colorsEvent.colorTwo);

                    Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(guild.getRoomId());

                    if (room != null && room.getUserCount() > 0) {
                        room.refreshGuild(guild);

                        room.refreshGuildColors(guild);
                    }
                    guild.needsUpdate = true;
                    Emulator.getThreading().run(guild);
                }
            }
        }
    }
}
