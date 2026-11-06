package polaris.messages.outgoing.guilds;

import java.text.SimpleDateFormat;
import java.util.Date;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.guilds.Guild;
import polaris.hotel.guilds.GuildMember;
import polaris.hotel.guilds.GuildMembershipStatus;
import polaris.hotel.guilds.GuildRank;
import polaris.hotel.permissions.Permission;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class GuildInfoComposer extends MessageComposer {
    private final Guild guild;
    private final GameClient client;
    private final boolean newWindow;
    private final GuildMember member;

    public GuildInfoComposer(Guild guild, GameClient client, boolean newWindow, GuildMember member) {
        this.guild = guild;
        this.client = client;
        this.newWindow = newWindow;
        this.member = member;
    }

    @Override
    protected ServerMessage composeInternal() {
        boolean adminPermissions = this.client.getUser().getUserStats().hasGuild(this.guild.getId())
                        && this.client.getUser().hasPermission(Permission.ACC_GUILD_ADMIN)
                || Emulator.getGameEnvironment()
                                .getGuildManager()
                                .getOnlyAdmins(guild)
                                .get(this.client.getUser().getUserInfo().getId())
                        != null;
        this.response.init(Outgoing.GuildInfoComposer);
        this.response.appendInt(this.guild.getId());
        this.response.appendBoolean(true);
        this.response.appendInt(this.guild.getState().state);
        this.response.appendString(this.guild.getName());
        this.response.appendString(this.guild.getDescription());
        this.response.appendString(this.guild.getBadge());
        this.response.appendInt(this.guild.getRoomId());
        this.response.appendString(this.guild.getRoomName());
        this.response.appendInt(
                (this.member == null ? GuildMembershipStatus.NOT_MEMBER : this.member.getMembershipStatus())
                        .getStatus());
        this.response.appendInt(this.guild.getMemberCount());
        this.response.appendBoolean(this.client.getUser().getUserStats().guild == this.guild.getId()); // favorite group
        this.response.appendString(
                new SimpleDateFormat("dd-MM-yyyy").format(new Date(this.guild.getDateCreated() * 1000L)));
        this.response.appendBoolean(adminPermissions
                || (this.guild.getOwnerId()
                        == this.client.getUser().getUserInfo().getId()));
        this.response.appendBoolean(adminPermissions
                || (this.member != null && (this.member.getRank().equals(GuildRank.ADMIN))));

        this.response.appendString(this.guild.getOwnerName());
        this.response.appendBoolean(this.newWindow);
        this.response.appendBoolean(this.guild.getRights());
        this.response.appendInt(
                (adminPermissions
                                || this.guild.getOwnerId()
                                        == this.client.getUser().getUserInfo().getId())
                        ? this.guild.getRequestCount()
                        : 0); // Guild invites count.
        this.response.appendBoolean(this.guild.hasForum());
        return this.response;
    }

    public Guild getGuild() {
        return guild;
    }

    public GameClient getClient() {
        return client;
    }

    public boolean isNewWindow() {
        return newWindow;
    }

    public GuildMember getMember() {
        return member;
    }
}
