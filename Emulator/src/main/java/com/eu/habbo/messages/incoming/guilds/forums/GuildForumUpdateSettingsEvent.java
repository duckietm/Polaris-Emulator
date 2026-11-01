package com.eu.habbo.messages.incoming.guilds.forums;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.guilds.Guild;
import com.eu.habbo.habbohotel.guilds.SettingsState;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.eu.habbo.messages.outgoing.guilds.forums.GuildForumDataComposer;
import com.eu.habbo.messages.outgoing.handshake.ConnectionErrorComposer;

public class GuildForumUpdateSettingsEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 2000;
    }

    @Override
    public void handle() throws Exception {
        int guildId = packet.readInt();
        int canRead = packet.readInt();
        int postMessages = packet.readInt();
        int postThreads = packet.readInt();
        int modForum = packet.readInt();

        if (!GuildForumInputGuard.isPositiveId(guildId)
                ||
                // Each value must fit its column: one outside the enum made the guild's whole UPDATE fail, name and
                // badge included, or left a row that did not load at the next start.
                !GuildForumInputGuard.isReadState(canRead)
                || !GuildForumInputGuard.isSettingsState(postMessages)
                || !GuildForumInputGuard.isSettingsState(postThreads)
                || !GuildForumInputGuard.isModerationState(modForum)) {
            this.client.sendResponse(new ConnectionErrorComposer(400));
            return;
        }

        Guild guild = Emulator.getGameEnvironment().getGuildManager().getGuild(guildId);

        if (guild == null) {
            this.client.sendResponse(new ConnectionErrorComposer(404));
            return;
        }

        if (guild.getOwnerId() != this.client.getHabbo().getHabboInfo().getId()) {
            this.client.sendResponse(new ConnectionErrorComposer(403));
            return;
        }

        guild.setReadForum(SettingsState.fromValue(canRead));
        guild.setPostMessages(SettingsState.fromValue(postMessages));
        guild.setPostThreads(SettingsState.fromValue(postThreads));
        guild.setModForum(SettingsState.fromValue(modForum));

        guild.needsUpdate = true;

        Emulator.getThreading().run(guild);

        this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FORUMS_FORUM_SETTINGS_UPDATED.key).compose());

        this.client.sendResponse(new GuildForumDataComposer(guild, this.client.getHabbo()));
    }
}
