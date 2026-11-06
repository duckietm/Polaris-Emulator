package polaris.messages.incoming.guilds;

import polaris.Emulator;
import polaris.hotel.guilds.Guild;
import polaris.hotel.guilds.GuildState;
import polaris.hotel.rooms.Room;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.guilds.GuildInfoComposer;
import polaris.messages.outgoing.guilds.GuildJoinErrorComposer;
import polaris.plugin.events.guilds.GuildUserJoinEvent;

public class RequestGuildJoinEvent extends MessageHandler {
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

        if (this.client.getUser().getUserStats().hasGuild(guildId)) return;

        Guild guild = Emulator.getGameEnvironment().getGuildManager().getGuild(guildId);

        if (guild == null) return;

        if (guild.getState() == GuildState.CLOSED || guild.getState() == GuildState.LARGE_CLOSED) {
            this.client.sendResponse(new GuildJoinErrorComposer(GuildJoinErrorComposer.GROUP_CLOSED));
            return;
        }

        GuildUserJoinEvent joinEvent = new GuildUserJoinEvent(
                guild, this.client.getUser().getUserInfo().getId(), this.client.getUser());
        Emulator.getPluginManager().fireEvent(joinEvent);

        if (joinEvent.isCancelled()) return;

        Emulator.getGameEnvironment().getGuildManager().joinGuild(guild, this.client, 0, false);
        this.client.sendResponse(new GuildInfoComposer(
                guild,
                this.client,
                false,
                Emulator.getGameEnvironment().getGuildManager().getGuildMember(guild, this.client.getUser())));

        Room room = this.client.getUser().getUserInfo().getCurrentRoom();

        if (room == null || room.getGuildId() != guildId) return;

        room.refreshRightsForUser(this.client.getUser());
    }
}
