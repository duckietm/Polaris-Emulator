package polaris.messages.incoming.guilds;

import polaris.Emulator;
import polaris.hotel.guilds.Guild;
import polaris.hotel.guilds.GuildMember;
import polaris.hotel.guilds.GuildRank;
import polaris.hotel.permissions.Permission;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.guilds.GuildMembersComposer;

public class RequestGuildMembersEvent extends MessageHandler {
    private static final int MAX_PAGE_ID = 1000;
    private static final int MAX_QUERY_LENGTH = 32;
    private static final int MAX_LEVEL_ID = 2; // 0 all, 1 admins, 2 pending
    private static final int BLOCKED_LEVEL_ID = 3; // the blocked list (AIR 13)

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        int groupId = this.packet.readInt();
        int pageId = this.packet.readInt();
        String query = this.packet.readString();
        int levelId = this.packet.readInt();
        boolean pageValid = !(pageId < 0 || pageId > MAX_PAGE_ID);
        boolean levelKnown = levelId >= 0 && (levelId <= MAX_LEVEL_ID || levelId == BLOCKED_LEVEL_ID);
        boolean queryValid = query != null && query.length() <= MAX_QUERY_LENGTH;
        if (!GuildInputGuard.isPositiveId(groupId) || !pageValid || !levelKnown || !queryValid) {
            return;
        }

        Guild g = Emulator.getGameEnvironment().getGuildManager().getGuild(groupId);

        if (g != null) {
            boolean isAdmin = this.client.getUser().hasPermission(Permission.ACC_GUILD_ADMIN);
            if (!isAdmin && this.client.getUser().getUserStats().hasGuild(g.getId())) {
                GuildMember member =
                        Emulator.getGameEnvironment().getGuildManager().getGuildMember(g, this.client.getUser());
                isAdmin = member != null
                        && (member.getRank().equals(GuildRank.OWNER)
                                || member.getRank().equals(GuildRank.ADMIN));
            }

            if (levelId >= 2 && !isAdmin) {
                levelId = 0; // pending and blocked lists are admin-only, like the official dropdown
            }

            this.client.sendResponse(new GuildMembersComposer(
                    g,
                    Emulator.getGameEnvironment().getGuildManager().getGuildMembers(g, pageId, levelId, query),
                    this.client.getUser(),
                    pageId,
                    levelId,
                    query,
                    isAdmin,
                    Emulator.getGameEnvironment().getGuildManager().getGuildMembersCount(g, pageId, levelId, query)));
        }
    }
}
