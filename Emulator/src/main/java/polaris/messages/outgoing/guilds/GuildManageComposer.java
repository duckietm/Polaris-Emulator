package polaris.messages.outgoing.guilds;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import polaris.hotel.guilds.Guild;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class GuildManageComposer extends MessageComposer {
    private final Guild guild;

    public GuildManageComposer(Guild guild) {
        this.guild = guild;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.GuildManageComposer);
        this.response.appendInt(1);
        this.response.appendInt(guild.getRoomId());
        this.response.appendString(guild.getRoomName());
        this.response.appendBoolean(false);
        this.response.appendBoolean(true);
        this.response.appendInt(this.guild.getId());
        this.response.appendString(this.guild.getName());
        this.response.appendString(this.guild.getDescription());
        this.response.appendInt(this.guild.getRoomId());
        this.response.appendInt(this.guild.getColorOne());
        this.response.appendInt(this.guild.getColorTwo());
        this.response.appendInt(this.guild.getState().state);
        this.response.appendInt(this.guild.getRights() ? 0 : 1);
        this.response.appendBoolean(false);
        this.response.appendString("");
        this.response.appendInt(5);
        String badge = this.guild.getBadge();
        Matcher matcher = Pattern.compile("[bst][0-9]{4,6}").matcher(badge);
        int partsWritten = 0;

        while (matcher.find() && partsWritten < 5) {
            String partCode = matcher.group();
            char type = partCode.charAt(0);
            boolean shortMethod = (partCode.length() == 6);

            int parsedPartId = Integer.parseInt(partCode.substring(1, shortMethod ? 3 : 4));
            int partId = ((type == 't') ? (parsedPartId + 100) : parsedPartId);
            int color = Integer.parseInt(partCode.substring(shortMethod ? 3 : 4, shortMethod ? 5 : 6));
            int position = (partCode.length() < 6)
                    ? 0
                    : Integer.parseInt(partCode.substring(shortMethod ? 5 : 6, shortMethod ? 6 : 7));

            this.response.appendInt(partId);
            this.response.appendInt(color);
            this.response.appendInt(position);
            partsWritten++;
        }

        while (partsWritten < 5) {
            this.response.appendInt(0);
            this.response.appendInt(0);
            this.response.appendInt(0);
            partsWritten++;
        }
        this.response.appendString(this.guild.getBadge());
        this.response.appendInt(this.guild.getMemberCount());
        this.response.appendBoolean(this.guild.hasForum());
        return this.response;
    }

    public Guild getGuild() {
        return guild;
    }
}
