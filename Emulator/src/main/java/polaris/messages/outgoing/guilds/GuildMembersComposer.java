package polaris.messages.outgoing.guilds;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.TimeZone;
import polaris.hotel.guilds.Guild;
import polaris.hotel.guilds.GuildMember;
import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class GuildMembersComposer extends MessageComposer {
    private final ArrayList<GuildMember> members;
    private final Guild guild;
    private final User session;
    private final int pageId;
    private final int level;
    private final String searchValue;
    private final boolean isAdmin;
    private final int totalCount;

    public GuildMembersComposer(
            Guild guild,
            ArrayList<GuildMember> members,
            User session,
            int pageId,
            int level,
            String searchValue,
            boolean isAdmin,
            int totalCount) {
        this.guild = guild;
        this.members = members;
        this.session = session;
        this.pageId = pageId;
        this.level = level;
        this.searchValue = searchValue;
        this.isAdmin = isAdmin;
        this.totalCount = totalCount;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.GuildMembersComposer);
        this.response.appendInt(this.guild.getId());
        this.response.appendString(this.guild.getName());
        this.response.appendInt(this.guild.getRoomId());
        this.response.appendString(this.guild.getBadge());
        this.response.appendInt(this.totalCount);
        this.response.appendInt(this.members.size());

        Calendar cal = Calendar.getInstance(TimeZone.getDefault());
        for (GuildMember member : this.members) {
            cal.setTimeInMillis(member.getJoinDate() * 1000L);
            this.response.appendInt(member.getRank().type);
            this.response.appendInt(member.getUserId());
            this.response.appendString(member.getUsername());
            this.response.appendString(member.getLook());
            this.response.appendString(
                    member.getRank().type < 3
                            ? cal.get(Calendar.DAY_OF_MONTH) + "/" + (cal.get(Calendar.MONTH) + 1) + "/"
                                    + cal.get(Calendar.YEAR)
                            : "");
        }

        this.response.appendBoolean(this.isAdmin);
        this.response.appendInt(14);
        this.response.appendInt(this.pageId);
        this.response.appendInt(this.level);
        this.response.appendString(this.searchValue);
        return this.response;
    }

    public ArrayList<GuildMember> getMembers() {
        return members;
    }

    public Guild getGuild() {
        return guild;
    }

    public User getSession() {
        return session;
    }

    public int getPageId() {
        return pageId;
    }

    public int getLevel() {
        return level;
    }

    public String getSearchValue() {
        return searchValue;
    }

    public boolean isAdmin() {
        return isAdmin;
    }

    public int getTotalCount() {
        return totalCount;
    }
}
