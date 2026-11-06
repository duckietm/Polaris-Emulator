package polaris.messages.incoming.guilds.forums;

import polaris.Emulator;
import polaris.hotel.guilds.Guild;
import polaris.hotel.guilds.GuildMember;
import polaris.hotel.permissions.Permission;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.generic.alerts.BubbleAlertComposer;
import polaris.messages.outgoing.generic.alerts.BubbleAlertKeys;
import polaris.messages.outgoing.guilds.forums.GuildForumDataComposer;
import polaris.messages.outgoing.guilds.forums.GuildForumThreadsComposer;
import polaris.messages.outgoing.handshake.ConnectionErrorComposer;

public class GuildForumThreadsEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        int guildId = packet.readInt();
        int index = packet.readInt();
        packet.readInt(); // Requested page size; the response currently applies its canonical limit.

        if (!GuildForumInputGuard.isPositiveId(guildId) || !GuildForumInputGuard.isValidThreadIndex(index)) {
            this.client.sendResponse(new ConnectionErrorComposer(400));
            return;
        }

        Guild guild = Emulator.getGameEnvironment().getGuildManager().getGuild(guildId);

        if (guild == null) {
            this.client.sendResponse(new ConnectionErrorComposer(404));
            return;
        }
        GuildMember member = Emulator.getGameEnvironment()
                .getGuildManager()
                .getGuildMember(guildId, this.client.getUser().getUserInfo().getId());
        boolean staff = this.client.getUser().hasPermission(Permission.ACC_MODTOOL_TICKET_Q);

        if (!guild.canUserReadForum(this.client.getUser().getUserInfo().getId(), member, staff)) {
            this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FORUMS_ACCESS_DENIED.key).compose());
            return;
        }

        this.client.sendResponse(new GuildForumDataComposer(guild, this.client.getUser()));
        this.client.sendResponse(new GuildForumThreadsComposer(guild, index));
    }
}
