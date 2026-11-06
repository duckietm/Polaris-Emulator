package polaris.messages.outgoing.rooms.users;

import java.util.Map;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RoomUsersGuildBadgesComposer extends MessageComposer {
    private final Map<Integer, String> guildBadges;

    public RoomUsersGuildBadgesComposer(Map<Integer, String> guildBadges) {
        this.guildBadges = guildBadges;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomUsersGuildBadgesComposer);
        this.response.appendInt(this.guildBadges.size());

        for (Map.Entry<Integer, String> guildBadge : this.guildBadges.entrySet()) {
            this.response.appendInt(guildBadge.getKey());
            this.response.appendString(guildBadge.getValue());
        }
        return this.response;
    }

    public Map<Integer, String> getGuildBadges() {
        return guildBadges;
    }
}
