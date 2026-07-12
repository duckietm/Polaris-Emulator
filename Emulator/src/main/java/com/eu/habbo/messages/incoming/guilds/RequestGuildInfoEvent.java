package com.eu.habbo.messages.incoming.guilds;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.guilds.Guild;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.guilds.GuildInfoComposer;

public class RequestGuildInfoEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        int guildId = this.packet.readInt();
        boolean newWindow = this.packet.readBoolean();

        if (!GuildInputGuard.isPositiveId(guildId)) {
            return;
        }

        Guild guild = Emulator.getGameEnvironment().getGuildManager().getGuild(guildId);

        if (guild != null) {
            this.client.sendResponse(new GuildInfoComposer(guild, this.client, newWindow, Emulator.getGameEnvironment().getGuildManager().getGuildMember(guild, this.client.getHabbo())));
        }
    }
}
