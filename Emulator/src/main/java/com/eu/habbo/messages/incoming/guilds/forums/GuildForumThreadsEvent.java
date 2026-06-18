package com.eu.habbo.messages.incoming.guilds.forums;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.guilds.Guild;
import com.eu.habbo.habbohotel.guilds.GuildMember;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.eu.habbo.messages.outgoing.guilds.forums.GuildForumDataComposer;
import com.eu.habbo.messages.outgoing.guilds.forums.GuildForumThreadsComposer;
import com.eu.habbo.messages.outgoing.handshake.ConnectionErrorComposer;

public class GuildForumThreadsEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        int guildId = packet.readInt();
        int index = packet.readInt();

        if (!GuildForumInputGuard.isPositiveId(guildId) || !GuildForumInputGuard.isValidThreadIndex(index)) {
            this.client.sendResponse(new ConnectionErrorComposer(400));
            return;
        }

        Guild guild = Emulator.getGameEnvironment().getGuildManager().getGuild(guildId);

        if (guild == null) {
            this.client.sendResponse(new ConnectionErrorComposer(404));
            return;
        }
        GuildMember member = Emulator.getGameEnvironment().getGuildManager().getGuildMember(guildId, this.client.getHabbo().getHabboInfo().getId());
        boolean staff = this.client.getHabbo().hasPermission(Permission.ACC_MODTOOL_TICKET_Q);

        if (!guild.canHabboReadForum(this.client.getHabbo().getHabboInfo().getId(), member, staff)) {
            this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FORUMS_ACCESS_DENIED.key).compose());
            return;
        }

        this.client.sendResponse(new GuildForumDataComposer(guild, this.client.getHabbo()));
        this.client.sendResponse(new GuildForumThreadsComposer(guild, index));
    }
}
