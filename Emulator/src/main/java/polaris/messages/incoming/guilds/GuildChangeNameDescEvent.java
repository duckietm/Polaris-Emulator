package polaris.messages.incoming.guilds;

import polaris.Emulator;
import polaris.hotel.guilds.Guild;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.messages.incoming.MessageHandler;
import polaris.plugin.events.guilds.GuildChangedNameEvent;

public class GuildChangeNameDescEvent extends MessageHandler {
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
                String newName = Emulator.getGameEnvironment()
                        .getWordFilter()
                        .filter(this.packet.readString(), this.client.getUser());
                String newDesc = Emulator.getGameEnvironment()
                        .getWordFilter()
                        .filter(this.packet.readString(), this.client.getUser());

                if (!GuildInputLimits.isValidGuildName(newName) || !GuildInputLimits.isValidGuildDescription(newDesc))
                    return;

                GuildChangedNameEvent nameEvent = new GuildChangedNameEvent(guild, newName, newDesc);
                Emulator.getPluginManager().fireEvent(nameEvent);

                if (nameEvent.isCancelled()) return;

                if (guild.getName().equals(nameEvent.name)
                        && guild.getDescription().equals(nameEvent.description)) return;

                if (!GuildInputLimits.isValidGuildName(nameEvent.name)
                        || !GuildInputLimits.isValidGuildDescription(nameEvent.description)) return;

                guild.setName(nameEvent.name);
                guild.setDescription(nameEvent.description);
                guild.needsUpdate = true;
                guild.run();

                Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(guild.getRoomId());

                if (room != null && !room.getCurrentUsers().isEmpty()) {
                    room.refreshGuild(guild);
                }
            }
        }
    }
}
